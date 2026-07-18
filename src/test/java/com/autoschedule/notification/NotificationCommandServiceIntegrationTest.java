package com.autoschedule.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import com.autoschedule.auth.domain.DevicePlatform;
import com.autoschedule.member.domain.Member;
import com.autoschedule.member.domain.MemberRole;
import com.autoschedule.member.domain.SocialProvider;
import com.autoschedule.member.repository.MemberRepository;
import com.autoschedule.notification.domain.FcmToken;
import com.autoschedule.notification.domain.NotificationType;
import com.autoschedule.notification.domain.PushPolicy;
import com.autoschedule.notification.dto.NotificationSendCommand;
import com.autoschedule.notification.repository.FcmTokenRepository;
import com.autoschedule.notification.service.FcmDeliveryProcessor;
import com.autoschedule.notification.service.NotificationCommandService;
import jakarta.persistence.EntityManagerFactory;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * 알림 생성과 FCM 발송 이벤트 발행 흐름을 검증한다.
 */
@SpringBootTest
class NotificationCommandServiceIntegrationTest {

    @Autowired
    private NotificationCommandService notificationCommandService;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private FcmTokenRepository fcmTokenRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @MockitoBean
    private FcmDeliveryProcessor fcmDeliveryProcessor;

    private Member worker;

    /**
     * 각 테스트가 독립적으로 실행되도록 알림, 토큰, 회원 데이터를 초기화한다.
     */
    @BeforeEach
    void setUp() {
        cleanupDatabase();
        worker = memberRepository.save(Member.create(
                SocialProvider.KAKAO,
                "command-worker-subject",
                "worker@test.com",
                "worker",
                "01020000000",
                MemberRole.WORKER
        ));
    }

    /**
     * 앱 내 알림 정책은 알림함에만 저장하고 FCM delivery를 만들지 않는다.
     */
    @Test
    void sendInAppOnlyNotificationStoresNotificationWithoutDelivery() {
        Long notificationId = notificationCommandService.sendToMember(
                worker.getId(),
                new NotificationSendCommand(
                        NotificationType.NOTICE,
                        PushPolicy.IN_APP_ONLY,
                        "공지 알림",
                        "새 공지가 등록되었습니다.",
                        Map.of("noticeId", "1")
                )
        );

        Integer notificationCount = jdbcTemplate.queryForObject(
                "select count(*) from notification where notification_id = ? and receiver_member_id = ? and push_policy = 'IN_APP_ONLY'",
                Integer.class,
                notificationId,
                worker.getId()
        );
        Integer deliveryCount = jdbcTemplate.queryForObject(
                "select count(*) from notification_delivery where notification_id = ?",
                Integer.class,
                notificationId
        );
        assertThat(notificationCount).isEqualTo(1);
        assertThat(deliveryCount).isZero();
        verify(fcmDeliveryProcessor, timeout(300).times(0)).process(any());
    }

    /**
     * PUSH 정책은 활성 FCM 토큰마다 PENDING delivery를 만들고 커밋 이후 발송 처리를 위임한다.
     */
    @Test
    void sendPushNotificationCreatesPendingDeliveryAndPublishesPushEvent() {
        FcmToken fcmToken = fcmTokenRepository.save(FcmToken.create(
                worker,
                "worker-device",
                "fcm-token",
                DevicePlatform.ANDROID,
                "1.0.0",
                LocalDateTime.now()
        ));

        Long notificationId = notificationCommandService.sendToMember(
                worker.getId(),
                new NotificationSendCommand(
                        NotificationType.NOTICE,
                        PushPolicy.PUSH,
                        "공지 알림",
                        "새 공지가 등록되었습니다.",
                        Map.of("noticeId", "1")
                )
        );

        Long deliveryId = jdbcTemplate.queryForObject(
                "select notification_delivery_id from notification_delivery where notification_id = ? and fcm_token_id = ?",
                Long.class,
                notificationId,
                fcmToken.getId()
        );
        String deliveryStatus = jdbcTemplate.queryForObject(
                "select status from notification_delivery where notification_id = ? and fcm_token_id = ?",
                String.class,
                notificationId,
                fcmToken.getId()
        );
        assertThat(deliveryStatus).isEqualTo("PENDING");
        verify(fcmDeliveryProcessor, timeout(1000))
                .process(argThat(deliveryIds -> deliveryIds.contains(deliveryId)));
    }

    /**
     * PUSH 정책이어도 활성 FCM 토큰이 없으면 delivery를 만들지 않고 발송 처리를 위임하지 않는다.
     */
    @Test
    void sendPushNotificationWithoutActiveTokenDoesNotPublishPushEvent() {
        Long notificationId = notificationCommandService.sendToMember(
                worker.getId(),
                new NotificationSendCommand(
                        NotificationType.NOTICE,
                        PushPolicy.PUSH,
                        "공지 알림",
                        "새 공지가 등록되었습니다.",
                        Map.of("noticeId", "1")
                )
        );

        Integer deliveryCount = jdbcTemplate.queryForObject(
                "select count(*) from notification_delivery where notification_id = ?",
                Integer.class,
                notificationId
        );
        assertThat(deliveryCount).isZero();
        verify(fcmDeliveryProcessor, timeout(300).times(0)).process(any());
    }

    /**
     * 여러 회원에게 PUSH 알림을 보낼 때 수신자와 FCM 토큰을 회원별로 반복 조회하지 않는다.
     */
    /**
     * 회원이 FCM 푸시 수신을 끈 경우 앱 내부 알림만 저장하고 FCM delivery는 만들지 않는다.
     */
    @Test
    void sendPushNotificationWhenMemberDisablesFcmPushStoresOnlyInAppNotification() {
        saveFcmToken(worker, "worker-device", "fcm-token");
        jdbcTemplate.update(
                """
                        insert into member_notification_setting
                            (member_id, fcm_push_enabled, created_at, updated_at)
                        values (?, false, now(), now())
                        """,
                worker.getId()
        );

        Long notificationId = notificationCommandService.sendToMember(
                worker.getId(),
                new NotificationSendCommand(
                        NotificationType.NOTICE,
                        PushPolicy.PUSH,
                        "공지 알림",
                        "새 공지가 등록되었습니다.",
                        Map.of("noticeId", "1")
                )
        );

        Integer notificationCount = jdbcTemplate.queryForObject(
                "select count(*) from notification where notification_id = ? and receiver_member_id = ?",
                Integer.class,
                notificationId,
                worker.getId()
        );
        Integer deliveryCount = jdbcTemplate.queryForObject(
                "select count(*) from notification_delivery where notification_id = ?",
                Integer.class,
                notificationId
        );
        assertThat(notificationCount).isOne();
        assertThat(deliveryCount).isZero();
        verify(fcmDeliveryProcessor, timeout(300).times(0)).process(any());
    }

    @Test
    void sendPushNotificationToMembersCreatesNotificationsAndDeliveriesWithBatchLookup() {
        Member secondWorker = saveMember("command-worker-subject-2", "01020000001");
        FcmToken firstToken = saveFcmToken(worker, "worker-device-1", "fcm-token-1");
        FcmToken secondToken = saveFcmToken(worker, "worker-device-2", "fcm-token-2");
        FcmToken thirdToken = saveFcmToken(secondWorker, "second-worker-device", "fcm-token-3");
        Statistics statistics = resetHibernateStatistics();

        List<Long> notificationIds = notificationCommandService.sendToMembers(
                List.of(worker.getId(), secondWorker.getId()),
                new NotificationSendCommand(
                        NotificationType.NOTICE,
                        PushPolicy.PUSH,
                        "공지 알림",
                        "새 공지가 등록되었습니다.",
                        Map.of("noticeId", "1")
                )
        );

        assertThat(notificationIds).hasSize(2);
        assertThat(statistics.getPrepareStatementCount()).isLessThanOrEqualTo(8);
        Integer notificationCount = jdbcTemplate.queryForObject(
                "select count(*) from notification where notification_id in (?, ?)",
                Integer.class,
                notificationIds.get(0),
                notificationIds.get(1)
        );
        Integer deliveryCount = jdbcTemplate.queryForObject(
                "select count(*) from notification_delivery where fcm_token_id in (?, ?, ?)",
                Integer.class,
                firstToken.getId(),
                secondToken.getId(),
                thirdToken.getId()
        );
        assertThat(notificationCount).isEqualTo(2);
        assertThat(deliveryCount).isEqualTo(3);
        verify(fcmDeliveryProcessor, timeout(1000))
                .process(argThat(deliveryIds -> deliveryIds.size() == 3));
    }

    /**
     * 테스트용 회원을 저장한다.
     */
    private Member saveMember(String socialSubject, String phoneNumber) {
        return memberRepository.save(Member.create(
                SocialProvider.KAKAO,
                socialSubject,
                socialSubject + "@test.com",
                "worker",
                phoneNumber,
                MemberRole.WORKER
        ));
    }

    /**
     * 테스트용 FCM 토큰을 저장한다.
     */
    private FcmToken saveFcmToken(Member member, String deviceId, String token) {
        return fcmTokenRepository.save(FcmToken.create(
                member,
                deviceId,
                token,
                DevicePlatform.ANDROID,
                "1.0.0",
                LocalDateTime.now()
        ));
    }

    /**
     * Hibernate SQL 실행 통계를 초기화하고 수집을 활성화한다.
     */
    private Statistics resetHibernateStatistics() {
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.setStatisticsEnabled(true);
        statistics.clear();
        return statistics;
    }

    /**
     * 테스트 테이블 데이터를 참조 순서에 맞춰 정리한다.
     */
    private void cleanupDatabase() {
        com.autoschedule.support.TestDatabaseCleaner.clean(jdbcTemplate);
    }

    /**
     * 현재 테스트 스키마에 테이블이 존재하는지 확인한다.
     */
    private boolean tableExists(String tableName) {
        Integer count = jdbcTemplate.queryForObject(
                """
                        select count(*)
                          from information_schema.tables
                         where table_schema = database()
                           and table_name = ?
                        """,
                Integer.class,
                tableName
        );
        return count != null && count > 0;
    }
}
