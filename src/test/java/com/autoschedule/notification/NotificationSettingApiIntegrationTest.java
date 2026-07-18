package com.autoschedule.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.autoschedule.auth.domain.TokenType;
import com.autoschedule.auth.jwt.JwtTokenProvider;
import com.autoschedule.member.domain.Member;
import com.autoschedule.member.domain.MemberRole;
import com.autoschedule.member.domain.SocialProvider;
import com.autoschedule.member.repository.MemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 회원의 FCM 푸시 수신 설정 API를 검증한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class NotificationSettingApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Member worker;

    /**
     * 각 테스트가 독립적으로 실행되도록 회원과 알림 설정 데이터를 초기화한다.
     */
    @BeforeEach
    void setUp() {
        com.autoschedule.support.TestDatabaseCleaner.clean(jdbcTemplate);
        worker = memberRepository.save(Member.create(
                SocialProvider.KAKAO,
                "notification-setting-worker",
                "worker@test.com",
                "worker",
                "01020000000",
                MemberRole.WORKER
        ));
    }

    /**
     * 알림 설정이 아직 없으면 기본값 true로 생성하고 반환한다.
     */
    @Test
    void getNotificationSettingCreatesDefaultEnabledSetting() throws Exception {
        mockMvc.perform(get("/api/members/me/notification-settings")
                        .header(HttpHeaders.AUTHORIZATION, bearer(worker)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fcmPushEnabled").value(true));

        Integer settingCount = jdbcTemplate.queryForObject(
                "select count(*) from member_notification_setting where member_id = ? and fcm_push_enabled = true",
                Integer.class,
                worker.getId()
        );
        assertThat(settingCount).isOne();
    }

    /**
     * 회원은 FCM 푸시 수신 여부를 끄고 다시 조회할 수 있다.
     */
    @Test
    void memberUpdatesFcmPushEnabledSetting() throws Exception {
        mockMvc.perform(patch("/api/members/me/notification-settings")
                        .header(HttpHeaders.AUTHORIZATION, bearer(worker))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fcmPushEnabled": false
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fcmPushEnabled").value(false));

        mockMvc.perform(get("/api/members/me/notification-settings")
                        .header(HttpHeaders.AUTHORIZATION, bearer(worker)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fcmPushEnabled").value(false));
    }

    /**
     * FCM 푸시 수신 설정 값은 필수다.
     */
    @Test
    void updateNotificationSettingRequiresFcmPushEnabled() throws Exception {
        mockMvc.perform(patch("/api/members/me/notification-settings")
                        .header(HttpHeaders.AUTHORIZATION, bearer(worker))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fcmPushEnabled": null
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("4000"))
                .andExpect(jsonPath("$.errors.length()").value(1));
    }

    /**
     * 테스트용 access token 값을 생성한다.
     */
    private String bearer(Member member) {
        return "Bearer " + jwtTokenProvider.createToken(member, TokenType.ACCESS, 1800);
    }
}
