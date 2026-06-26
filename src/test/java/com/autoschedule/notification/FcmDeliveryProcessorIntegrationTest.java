package com.autoschedule.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.autoschedule.auth.domain.DevicePlatform;
import com.autoschedule.member.domain.Member;
import com.autoschedule.member.domain.MemberRole;
import com.autoschedule.member.domain.SocialProvider;
import com.autoschedule.member.repository.MemberRepository;
import com.autoschedule.notification.domain.FcmToken;
import com.autoschedule.notification.domain.Notification;
import com.autoschedule.notification.domain.NotificationDelivery;
import com.autoschedule.notification.domain.NotificationType;
import com.autoschedule.notification.domain.PushPolicy;
import com.autoschedule.notification.infra.FcmSendResult;
import com.autoschedule.notification.infra.FcmSender;
import com.autoschedule.notification.repository.FcmTokenRepository;
import com.autoschedule.notification.repository.NotificationDeliveryRepository;
import com.autoschedule.notification.repository.NotificationRepository;
import com.autoschedule.notification.service.FcmDeliveryProcessor;
import jakarta.persistence.EntityManagerFactory;
import java.time.LocalDateTime;
import java.util.List;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * PENDING FCM delivery의 실제 발송과 결과 반영을 검증한다.
 */
@SpringBootTest
class FcmDeliveryProcessorIntegrationTest {

    @Autowired
    private FcmDeliveryProcessor fcmDeliveryProcessor;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private FcmTokenRepository fcmTokenRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private NotificationDeliveryRepository notificationDeliveryRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @MockitoBean
    private FcmSender fcmSender;

    private Member worker;

    /**
     * 각 테스트가 독립적으로 실행되도록 알림, 토큰, 회원 데이터를 초기화한다.
     */
    @BeforeEach
    void setUp() {
        cleanupDatabase();
        worker = memberRepository.save(Member.create(
                SocialProvider.KAKAO,
                "processor-worker-subject",
                "worker@test.com",
                "worker",
                "01020000000",
                MemberRole.WORKER
        ));
    }

    /**
     * FCM 발송 성공 시 delivery를 SUCCESS로 변경하고 provider message id를 저장한다.
     */
    @Test
    void processPendingDeliveryStoresSuccessfulResult() {
        FcmToken fcmToken = saveFcmToken("worker-device", "fcm-token", DevicePlatform.ANDROID);
        NotificationDelivery delivery = savePendingDelivery(fcmToken);
        when(fcmSender.send(any())).thenReturn(FcmSendResult.success("provider-message-id"));

        fcmDeliveryProcessor.process(List.of(delivery.getId()));

        String deliveryStatus = jdbcTemplate.queryForObject(
                "select status from notification_delivery where notification_delivery_id = ?",
                String.class,
                delivery.getId()
        );
        String providerMessageId = jdbcTemplate.queryForObject(
                "select provider_message_id from notification_delivery where notification_delivery_id = ?",
                String.class,
                delivery.getId()
        );
        assertThat(deliveryStatus).isEqualTo("SUCCESS");
        assertThat(providerMessageId).isEqualTo("provider-message-id");
    }

    /**
     * FCM 발송 실패 시 delivery를 FAILED로 변경하고 에러 정보를 저장한다.
     */
    @Test
    void processPendingDeliveryStoresFailureResult() {
        FcmToken fcmToken = saveFcmToken("worker-device", "fcm-token", DevicePlatform.IOS);
        NotificationDelivery delivery = savePendingDelivery(fcmToken);
        when(fcmSender.send(any())).thenReturn(FcmSendResult.failure(
                "INTERNAL",
                "FCM 서버 오류",
                false
        ));

        fcmDeliveryProcessor.process(List.of(delivery.getId()));

        String deliveryStatus = jdbcTemplate.queryForObject(
                "select status from notification_delivery where notification_delivery_id = ?",
                String.class,
                delivery.getId()
        );
        String errorCode = jdbcTemplate.queryForObject(
                "select error_code from notification_delivery where notification_delivery_id = ?",
                String.class,
                delivery.getId()
        );
        assertThat(deliveryStatus).isEqualTo("FAILED");
        assertThat(errorCode).isEqualTo("INTERNAL");
    }

    /**
     * FCM이 무효 토큰이라고 응답하면 delivery 실패와 토큰 비활성화를 함께 처리한다.
     */
    @Test
    void processPendingDeliveryDeactivatesInvalidToken() {
        FcmToken fcmToken = saveFcmToken("worker-device", "fcm-token", DevicePlatform.IOS);
        NotificationDelivery delivery = savePendingDelivery(fcmToken);
        when(fcmSender.send(any())).thenReturn(FcmSendResult.failure(
                "UNREGISTERED",
                "등록되지 않은 FCM 토큰입니다.",
                true
        ));

        fcmDeliveryProcessor.process(List.of(delivery.getId()));

        String deliveryStatus = jdbcTemplate.queryForObject(
                "select status from notification_delivery where notification_delivery_id = ?",
                String.class,
                delivery.getId()
        );
        String tokenStatus = jdbcTemplate.queryForObject(
                "select status from fcm_token where fcm_token_id = ?",
                String.class,
                fcmToken.getId()
        );
        assertThat(deliveryStatus).isEqualTo("FAILED");
        assertThat(tokenStatus).isEqualTo("INACTIVE");
    }

    /**
     * 여러 delivery를 한 번에 처리할 때 notification과 FCM 토큰을 delivery별로 반복 조회하지 않는다.
     */
    @Test
    void processPendingDeliveriesUsesBatchLookupForNotificationsAndTokens() {
        Notification notification = saveNotification();
        FcmToken firstToken = saveFcmToken("worker-device-1", "fcm-token-1", DevicePlatform.ANDROID);
        FcmToken secondToken = saveFcmToken("worker-device-2", "fcm-token-2", DevicePlatform.ANDROID);
        FcmToken thirdToken = saveFcmToken("worker-device-3", "fcm-token-3", DevicePlatform.IOS);
        NotificationDelivery firstDelivery = savePendingDelivery(notification, firstToken);
        NotificationDelivery secondDelivery = savePendingDelivery(notification, secondToken);
        NotificationDelivery thirdDelivery = savePendingDelivery(notification, thirdToken);
        when(fcmSender.send(any())).thenReturn(FcmSendResult.success("provider-message-id"));
        Statistics statistics = resetHibernateStatistics();

        fcmDeliveryProcessor.process(List.of(
                firstDelivery.getId(),
                secondDelivery.getId(),
                thirdDelivery.getId()
        ));

        assertThat(statistics.getPrepareStatementCount()).isLessThanOrEqualTo(9);
        Integer successCount = jdbcTemplate.queryForObject(
                "select count(*) from notification_delivery where status = 'SUCCESS'",
                Integer.class
        );
        assertThat(successCount).isEqualTo(3);
    }

    /**
     * 테스트용 활성 FCM 토큰을 저장한다.
     */
    private FcmToken saveFcmToken(String deviceId, String token, DevicePlatform platform) {
        return fcmTokenRepository.save(FcmToken.create(
                worker,
                deviceId,
                token,
                platform,
                "1.0.0",
                LocalDateTime.now()
        ));
    }

    /**
     * 테스트용 알림과 PENDING delivery를 저장한다.
     */
    private NotificationDelivery savePendingDelivery(FcmToken fcmToken) {
        return savePendingDelivery(saveNotification(), fcmToken);
    }

    /**
     * 테스트용 알림을 저장한다.
     */
    private Notification saveNotification() {
        return notificationRepository.save(Notification.create(
                worker,
                NotificationType.NOTICE,
                PushPolicy.PUSH,
                "공지 알림",
                "새 공지가 등록되었습니다.",
                null
        ));
    }

    /**
     * 지정한 알림과 FCM 토큰으로 테스트용 PENDING delivery를 저장한다.
     */
    private NotificationDelivery savePendingDelivery(Notification notification, FcmToken fcmToken) {
        return notificationDeliveryRepository.save(NotificationDelivery.createFcmPending(
                notification,
                fcmToken.getId(),
                null
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
