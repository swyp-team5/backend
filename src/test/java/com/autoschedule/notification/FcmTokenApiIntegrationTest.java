package com.autoschedule.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
 * FCM 토큰 등록, 갱신, 비활성화 API를 검증한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class FcmTokenApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Member owner;
    private Member worker;

    /**
     * 각 테스트가 독립적으로 실행되도록 FCM 토큰과 회원 데이터를 초기화한다.
     */
    @BeforeEach
    void setUp() {
        cleanupDatabase();
        owner = memberRepository.save(Member.create(
                SocialProvider.GOOGLE,
                "fcm-owner-subject",
                "owner@test.com",
                "owner",
                "01010000000",
                MemberRole.OWNER
        ));
        worker = memberRepository.save(Member.create(
                SocialProvider.KAKAO,
                "fcm-worker-subject",
                "worker@test.com",
                "worker",
                "01020000000",
                MemberRole.WORKER
        ));
    }

    /**
     * 회원은 기기별 FCM 토큰을 등록할 수 있다.
     */
    @Test
    void memberRegistersFcmToken() throws Exception {
        mockMvc.perform(post("/api/fcm-tokens")
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(fcmTokenBody("owner-device", "token-1", "ANDROID", "1.0.0")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fcmTokenId").isNumber())
                .andExpect(jsonPath("$.deviceId").value("owner-device"))
                .andExpect(jsonPath("$.platform").value("ANDROID"))
                .andExpect(jsonPath("$.appVersion").value("1.0.0"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        Integer activeCount = jdbcTemplate.queryForObject(
                "select count(*) from fcm_token where member_id = ? and device_id = ? and token = ? and status = 'ACTIVE'",
                Integer.class,
                owner.getId(),
                "owner-device",
                "token-1"
        );
        assertThat(activeCount).isEqualTo(1);
    }

    /**
     * 같은 회원의 같은 deviceId로 다시 등록하면 기존 row를 갱신한다.
     */
    @Test
    void memberUpdatesFcmTokenForSameDevice() throws Exception {
        registerToken(owner, "owner-device", "old-token", "ANDROID", "1.0.0");

        mockMvc.perform(post("/api/fcm-tokens")
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(fcmTokenBody("owner-device", "new-token", "IOS", "1.1.0")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deviceId").value("owner-device"))
                .andExpect(jsonPath("$.platform").value("IOS"))
                .andExpect(jsonPath("$.appVersion").value("1.1.0"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        Integer rowCount = jdbcTemplate.queryForObject(
                "select count(*) from fcm_token where member_id = ? and device_id = ?",
                Integer.class,
                owner.getId(),
                "owner-device"
        );
        String token = jdbcTemplate.queryForObject(
                "select token from fcm_token where member_id = ? and device_id = ?",
                String.class,
                owner.getId(),
                "owner-device"
        );
        assertThat(rowCount).isEqualTo(1);
        assertThat(token).isEqualTo("new-token");
    }

    /**
     * 비활성화된 같은 기기 토큰을 다시 등록하면 기존 row가 ACTIVE 상태로 복구된다.
     */
    @Test
    void memberReactivatesInactiveFcmTokenForSameDevice() throws Exception {
        registerToken(owner, "owner-device", "old-token", "ANDROID", "1.0.0");
        mockMvc.perform(delete("/api/fcm-tokens/devices/{deviceId}", "owner-device")
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/fcm-tokens")
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(fcmTokenBody("owner-device", "new-token", "IOS", "1.1.0")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.platform").value("IOS"));

        Integer rowCount = jdbcTemplate.queryForObject(
                "select count(*) from fcm_token where member_id = ? and device_id = ?",
                Integer.class,
                owner.getId(),
                "owner-device"
        );
        String deletedAt = jdbcTemplate.queryForObject(
                "select cast(deleted_at as char) from fcm_token where member_id = ? and device_id = ?",
                String.class,
                owner.getId(),
                "owner-device"
        );
        assertThat(rowCount).isOne();
        assertThat(deletedAt).isNull();
    }

    /**
     * 회원은 본인 기기의 FCM 토큰을 비활성화할 수 있다.
     */
    @Test
    void memberDeactivatesOwnFcmToken() throws Exception {
        registerToken(owner, "owner-device", "token-1", "ANDROID", "1.0.0");

        mockMvc.perform(delete("/api/fcm-tokens/devices/{deviceId}", "owner-device")
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isNoContent());

        String status = jdbcTemplate.queryForObject(
                "select status from fcm_token where member_id = ? and device_id = ?",
                String.class,
                owner.getId(),
                "owner-device"
        );
        assertThat(status).isEqualTo("INACTIVE");
    }

    /**
     * 다른 회원의 같은 deviceId 삭제 요청은 해당 회원의 토큰에 영향을 주지 않는다.
     */
    @Test
    void memberCannotDeactivateOtherMembersFcmToken() throws Exception {
        registerToken(owner, "shared-device", "owner-token", "ANDROID", "1.0.0");

        mockMvc.perform(delete("/api/fcm-tokens/devices/{deviceId}", "shared-device")
                        .header(HttpHeaders.AUTHORIZATION, bearer(worker)))
                .andExpect(status().isNoContent());

        String ownerStatus = jdbcTemplate.queryForObject(
                "select status from fcm_token where member_id = ? and device_id = ?",
                String.class,
                owner.getId(),
                "shared-device"
        );
        assertThat(ownerStatus).isEqualTo("ACTIVE");
    }

    /**
     * FCM 토큰 등록 요청 필드는 실무적인 메시지로 검증된다.
     */
    @Test
    void fcmTokenRegisterRequestValidationFails() throws Exception {
        mockMvc.perform(post("/api/fcm-tokens")
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "deviceId": "",
                                  "token": "",
                                  "platform": null,
                                  "appVersion": ""
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("4000"))
                .andExpect(jsonPath("$.errors.length()").value(4));
    }

    /**
     * FCM 토큰 등록 요청을 수행한다.
     */
    private void registerToken(Member member, String deviceId, String token, String platform, String appVersion)
            throws Exception {
        mockMvc.perform(post("/api/fcm-tokens")
                        .header(HttpHeaders.AUTHORIZATION, bearer(member))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(fcmTokenBody(deviceId, token, platform, appVersion)))
                .andExpect(status().isOk());
    }

    /**
     * FCM 토큰 등록 JSON 본문을 생성한다.
     */
    private String fcmTokenBody(String deviceId, String token, String platform, String appVersion) {
        return """
                {
                  "deviceId": "%s",
                  "token": "%s",
                  "platform": "%s",
                  "appVersion": "%s"
                }
                """.formatted(deviceId, token, platform, appVersion);
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
