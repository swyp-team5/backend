package com.autoschedule.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.autoschedule.auth.domain.DevicePlatform;
import com.autoschedule.auth.domain.TokenType;
import com.autoschedule.auth.jwt.JwtTokenProvider;
import com.autoschedule.member.domain.Member;
import com.autoschedule.member.domain.MemberRole;
import com.autoschedule.member.domain.SocialProvider;
import com.autoschedule.member.repository.MemberRepository;
import com.autoschedule.notification.domain.FcmToken;
import com.autoschedule.notification.repository.FcmTokenRepository;
import com.autoschedule.notification.service.FcmDeliveryProcessor;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 회원 알림함 조회와 읽음 처리 API를 검증한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class NotificationApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private FcmTokenRepository fcmTokenRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private FcmDeliveryProcessor fcmDeliveryProcessor;

    private Member owner;
    private Member worker;

    /**
     * 각 테스트가 독립적으로 실행되도록 알림과 회원 데이터를 초기화한다.
     */
    @BeforeEach
    void setUp() {
        cleanupDatabase();
        owner = memberRepository.save(Member.create(
                SocialProvider.GOOGLE,
                "notification-owner-subject",
                "owner@test.com",
                "owner",
                "01010000000",
                MemberRole.OWNER
        ));
        worker = memberRepository.save(Member.create(
                SocialProvider.KAKAO,
                "notification-worker-subject",
                "worker@test.com",
                "worker",
                "01020000000",
                MemberRole.WORKER
        ));
    }

    /**
     * 회원은 본인 알림을 최신순 커서 방식으로 조회한다.
     */
    @Test
    void memberReadsNotificationsByCursor() throws Exception {
        Long oldNotificationId = insertNotification(owner, "오래된 알림", "오래된 내용");
        Long latestNotificationId = insertNotification(owner, "최신 알림", "최신 내용");
        insertNotification(worker, "다른 회원 알림", "노출되면 안됨");

        mockMvc.perform(get("/api/notifications")
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].notificationId").value(latestNotificationId))
                .andExpect(jsonPath("$.content[0].title").value("최신 알림"))
                .andExpect(jsonPath("$.content[0].read").value(false))
                .andExpect(jsonPath("$.nextCursorId").value(latestNotificationId))
                .andExpect(jsonPath("$.hasNext").value(true));

        mockMvc.perform(get("/api/notifications")
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .param("cursorId", String.valueOf(latestNotificationId))
                        .param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].notificationId").value(oldNotificationId))
                .andExpect(jsonPath("$.hasNext").value(false));
    }

    /**
     * 알림이 없는 회원은 빈 알림함을 조회한다.
     */
    @Test
    void memberReadsEmptyNotificationInbox() throws Exception {
        mockMvc.perform(get("/api/notifications")
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0))
                .andExpect(jsonPath("$.nextCursorId").doesNotExist())
                .andExpect(jsonPath("$.hasNext").value(false));
    }

    /**
     * 회원은 본인 알림 단건을 읽음 처리할 수 있다.
     */
    @Test
    void memberMarksNotificationAsRead() throws Exception {
        Long notificationId = insertNotification(owner, "읽음 알림", "읽음 처리할 내용");

        mockMvc.perform(patch("/api/notifications/{notificationId}/read", notificationId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notificationId").value(notificationId))
                .andExpect(jsonPath("$.read").value(true))
                .andExpect(jsonPath("$.readAt").exists());

        Timestamp readAt = jdbcTemplate.queryForObject(
                "select read_at from notification where notification_id = ?",
                Timestamp.class,
                notificationId
        );
        assertThat(readAt).isNotNull();
    }

    /**
     * 회원은 본인의 모든 미읽음 알림을 읽음 처리할 수 있다.
     */
    @Test
    void memberMarksAllNotificationsAsRead() throws Exception {
        insertNotification(owner, "첫 알림", "첫 내용");
        insertNotification(owner, "둘째 알림", "둘째 내용");
        insertNotification(worker, "다른 회원 알림", "노출되면 안됨");

        mockMvc.perform(patch("/api/notifications/read-all")
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isNoContent());

        Integer unreadCount = jdbcTemplate.queryForObject(
                "select count(*) from notification where receiver_member_id = ? and read_at is null",
                Integer.class,
                owner.getId()
        );
        Integer workerUnreadCount = jdbcTemplate.queryForObject(
                "select count(*) from notification where receiver_member_id = ? and read_at is null",
                Integer.class,
                worker.getId()
        );
        assertThat(unreadCount).isZero();
        assertThat(workerUnreadCount).isEqualTo(1);
    }

    /**
     * 다른 회원의 알림은 읽음 처리할 수 없다.
     */
    @Test
    void memberCannotReadOtherMembersNotification() throws Exception {
        Long workerNotificationId = insertNotification(worker, "근무자 알림", "근무자 내용");

        mockMvc.perform(patch("/api/notifications/{notificationId}/read", workerNotificationId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("4004"));
    }

    /**
     * 로그인 회원은 비즈니스 이벤트 없이 본인에게 FCM 테스트 푸시를 요청할 수 있다.
     */
    @Test
    void memberSendsTestPushToSelf() throws Exception {
        FcmToken fcmToken = fcmTokenRepository.save(FcmToken.create(
                worker,
                "worker-device",
                "worker-fcm-token",
                DevicePlatform.ANDROID,
                "1.0.0",
                LocalDateTime.now()
        ));

        mockMvc.perform(post("/api/notifications/test-push")
                        .header(HttpHeaders.AUTHORIZATION, bearer(worker))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "FCM 테스트",
                                  "body": "테스트 푸시가 도착했어요.",
                                  "data": {
                                    "type": "FCM_TEST"
                                  }
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notificationId").isNumber());

        Long notificationId = jdbcTemplate.queryForObject(
                """
                        select notification_id
                          from notification
                         where receiver_member_id = ?
                           and notification_type = 'FCM_TEST'
                           and push_policy = 'PUSH'
                           and title = 'FCM 테스트'
                        """,
                Long.class,
                worker.getId()
        );
        Long deliveryId = jdbcTemplate.queryForObject(
                """
                        select notification_delivery_id
                          from notification_delivery
                         where notification_id = ?
                           and fcm_token_id = ?
                           and status = 'PENDING'
                        """,
                Long.class,
                notificationId,
                fcmToken.getId()
        );

        assertThat(notificationId).isNotNull();
        assertThat(deliveryId).isNotNull();
        verify(fcmDeliveryProcessor, timeout(1000))
                .process(argThat(deliveryIds -> deliveryIds.contains(deliveryId)));
    }

    /**
     * 테스트 푸시 요청 필드는 클라이언트가 활용할 수 있는 검증 메시지로 검증된다.
     */
    @Test
    void testPushRequestValidationFails() throws Exception {
        mockMvc.perform(post("/api/notifications/test-push")
                        .header(HttpHeaders.AUTHORIZATION, bearer(worker))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "",
                                  "body": ""
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("4000"))
                .andExpect(jsonPath("$.errors.length()").value(2));

        verify(fcmDeliveryProcessor, timeout(300).times(0)).process(any());
    }

    /**
     * 테스트용 알림 데이터를 생성한다.
     */
    private Long insertNotification(Member receiver, String title, String body) {
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update(
                """
                        insert into notification
                            (receiver_member_id, notification_type, push_policy, title, body, data, read_at, status, created_at, updated_at, deleted_at)
                        values
                            (?, 'NOTICE', 'PUSH', ?, ?, null, null, 'ACTIVE', ?, ?, null)
                        """,
                receiver.getId(),
                title,
                body,
                now,
                now
        );
        return jdbcTemplate.queryForObject("select last_insert_id()", Long.class);
    }

    /**
     * 테스트용 access token 값을 생성한다.
     */
    private String bearer(Member member) {
        return "Bearer " + jwtTokenProvider.createToken(member, TokenType.ACCESS, 1800);
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
