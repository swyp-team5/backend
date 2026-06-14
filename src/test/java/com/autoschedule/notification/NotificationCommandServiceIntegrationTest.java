package com.autoschedule.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.autoschedule.member.domain.Member;
import com.autoschedule.member.domain.MemberRole;
import com.autoschedule.member.domain.SocialProvider;
import com.autoschedule.member.repository.MemberRepository;
import com.autoschedule.notification.domain.FcmToken;
import com.autoschedule.notification.domain.NotificationType;
import com.autoschedule.notification.domain.PushPolicy;
import com.autoschedule.notification.dto.NotificationSendCommand;
import com.autoschedule.notification.infra.FcmSendResult;
import com.autoschedule.notification.infra.FcmSender;
import com.autoschedule.notification.repository.FcmTokenRepository;
import com.autoschedule.notification.service.NotificationCommandService;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * 알림 생성, FCM 발송 시도, 발송 이력 저장 흐름을 검증한다.
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
    }

    /**
     * PUSH 정책은 활성 FCM 토큰마다 성공 delivery 이력을 저장한다.
     */
    @Test
    void sendPushNotificationStoresSuccessfulDelivery() {
        FcmToken fcmToken = fcmTokenRepository.save(FcmToken.create(
                worker,
                "worker-device",
                "fcm-token",
                com.autoschedule.auth.domain.DevicePlatform.ANDROID,
                "1.0.0",
                java.time.LocalDateTime.now()
        ));
        when(fcmSender.send(any())).thenReturn(FcmSendResult.success("provider-message-id"));

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

        String deliveryStatus = jdbcTemplate.queryForObject(
                "select status from notification_delivery where notification_id = ? and fcm_token_id = ?",
                String.class,
                notificationId,
                fcmToken.getId()
        );
        String providerMessageId = jdbcTemplate.queryForObject(
                "select provider_message_id from notification_delivery where notification_id = ? and fcm_token_id = ?",
                String.class,
                notificationId,
                fcmToken.getId()
        );
        assertThat(deliveryStatus).isEqualTo("SUCCESS");
        assertThat(providerMessageId).isEqualTo("provider-message-id");
    }

    /**
     * FCM이 등록되지 않은 토큰이라고 응답하면 delivery 실패 이력과 토큰 비활성화를 함께 처리한다.
     */
    @Test
    void sendPushNotificationDeactivatesUnregisteredToken() {
        FcmToken fcmToken = fcmTokenRepository.save(FcmToken.create(
                worker,
                "worker-device",
                "fcm-token",
                com.autoschedule.auth.domain.DevicePlatform.IOS,
                "1.0.0",
                java.time.LocalDateTime.now()
        ));
        when(fcmSender.send(any())).thenReturn(FcmSendResult.failure(
                "UNREGISTERED",
                "등록되지 않은 FCM 토큰입니다.",
                true
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

        String deliveryStatus = jdbcTemplate.queryForObject(
                "select status from notification_delivery where notification_id = ? and fcm_token_id = ?",
                String.class,
                notificationId,
                fcmToken.getId()
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
     * 테스트 테이블 데이터를 참조 순서에 맞춰 정리한다.
     */
    private void cleanupDatabase() {
        if (tableExists("notification_delivery")) {
            jdbcTemplate.update("delete from notification_delivery");
        }
        if (tableExists("notification")) {
            jdbcTemplate.update("delete from notification");
        }
        if (tableExists("fcm_token")) {
            jdbcTemplate.update("delete from fcm_token");
        }
        jdbcTemplate.update("delete from member");
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
