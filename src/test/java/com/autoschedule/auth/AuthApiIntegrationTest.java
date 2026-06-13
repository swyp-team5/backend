package com.autoschedule.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.autoschedule.auth.jwt.TokenHashService;
import com.autoschedule.auth.social.SocialAuthCommand;
import com.autoschedule.auth.social.SocialAuthProvider;
import com.autoschedule.auth.social.SocialAuthProviderRegistry;
import com.autoschedule.auth.social.SocialUserInfo;
import com.autoschedule.member.domain.SocialProvider;
import com.autoschedule.member.repository.MemberRepository;
import com.autoschedule.terms.domain.Terms;
import com.autoschedule.terms.domain.TermsStatus;
import com.autoschedule.terms.domain.TermsType;
import com.autoschedule.terms.repository.TermsRepository;
import com.autoschedule.workplace.repository.WorkPlaceRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 소셜 로그인과 회원가입 API가 명세에 맞는 응답과 영속 상태를 만드는지 검증한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class AuthApiIntegrationTest {

    @Container
    private static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7.2-alpine")
            .withExposedPorts(6379);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private WorkPlaceRepository workPlaceRepository;

    @Autowired
    private TermsRepository termsRepository;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TokenHashService tokenHashService;

    @MockitoBean
    private SocialAuthProviderRegistry socialAuthProviderRegistry;

    private Long commonAgeTermsId;
    private Long commonUseTermsId;
    private Long ownerPrivacyTermsId;

    /**
     * 인증 API 테스트에서 사용할 Redis Testcontainers 접속 정보를 주입한다.
     */
    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    /**
     * 테스트마다 소셜 인증 전략과 필수 약관 데이터를 초기화한다.
     */
    @BeforeEach
    void setUp() {
        cleanupDatabase();

        Set<String> refreshTokenKeys = redisTemplate.keys("auth:refresh-token:*");
        if (refreshTokenKeys != null && !refreshTokenKeys.isEmpty()) {
            redisTemplate.delete(refreshTokenKeys);
        }

        when(socialAuthProviderRegistry.get(SocialProvider.GOOGLE))
                .thenReturn(new StubSocialAuthProvider(
                        SocialProvider.GOOGLE,
                        new SocialUserInfo(SocialProvider.GOOGLE, "google-subject", "google@test.com")
                ));
        when(socialAuthProviderRegistry.get(SocialProvider.KAKAO))
                .thenReturn(new StubSocialAuthProvider(
                        SocialProvider.KAKAO,
                        new SocialUserInfo(SocialProvider.KAKAO, "kakao-subject", "kakao@test.com")
                ));
        when(socialAuthProviderRegistry.get(SocialProvider.APPLE))
                .thenReturn(new StubSocialAuthProvider(
                        SocialProvider.APPLE,
                        new SocialUserInfo(SocialProvider.APPLE, "apple-subject", null)
                ));

        commonAgeTermsId = termsRepository.save(Terms.create(TermsType.COMMON, "age", true, TermsStatus.ACTIVE, "content", "1.0.0")).getId();
        commonUseTermsId = termsRepository.save(Terms.create(TermsType.COMMON, "use", true, TermsStatus.ACTIVE, "content", "1.0.0")).getId();
        ownerPrivacyTermsId = termsRepository.save(Terms.create(TermsType.OWNER, "privacy", true, TermsStatus.ACTIVE, "content", "1.0.0")).getId();
    }

    /**
     * 기존 회원이 없으면 회원 데이터를 만들지 않고 SIGNUP_REQUIRED를 반환한다.
     */
    @Test
    void socialLoginReturnsSignupRequiredWhenMemberDoesNotExist() throws Exception {
        mockMvc.perform(post("/api/auth/social-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "provider": "GOOGLE",
                                  "idToken": "google-id-token",
                                  "device": {
                                    "deviceId": "device-1",
                                    "platform": "ANDROID",
                                    "appVersion": "1.0.0"
                                  }
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SIGNUP_REQUIRED"))
                .andExpect(jsonPath("$.accessToken").doesNotExist());

        assertThat(memberRepository.count()).isZero();
    }

    /**
     * 소셜 로그인 요청 DTO 검증 실패 시 모바일 클라이언트가 사용할 수 있는 한글 필드 메시지를 응답한다.
     */
    @Test
    void socialLoginValidationFailureReturnsFieldMessages() throws Exception {
        mockMvc.perform(post("/api/auth/social-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "provider": null,
                                  "device": {
                                    "deviceId": "",
                                    "platform": null,
                                    "appVersion": ""
                                  }
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("4000"))
                .andExpect(jsonPath("$.errors[?(@.field == 'provider')].message")
                        .value(hasItem("소셜 로그인 제공자는 필수입니다.")))
                .andExpect(jsonPath("$.errors[?(@.field == 'device.deviceId')].message")
                        .value(hasItem("기기 ID는 필수입니다.")))
                .andExpect(jsonPath("$.errors[?(@.field == 'device.platform')].message")
                        .value(hasItem("기기 플랫폼은 필수입니다.")))
                .andExpect(jsonPath("$.errors[?(@.field == 'device.appVersion')].message")
                        .value(hasItem("앱 버전은 필수입니다.")));
    }

    /**
     * Google 로그인 요청에서 idToken이 없으면 잘못된 요청으로 거절한다.
     */
    @Test
    void googleSocialLoginRejectsMissingIdToken() throws Exception {
        mockMvc.perform(post("/api/auth/social-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "provider": "GOOGLE",
                                  "device": {
                                    "deviceId": "device-invalid",
                                    "platform": "ANDROID",
                                    "appVersion": "1.0.0"
                                  }
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("4001"));
    }

    /**
     * 사장 회원가입은 회원, 사업장, 사장 소속을 만들고 즉시 로그인 토큰을 반환한다.
     */
    @Test
    void ownerSignupCreatesOwnerWorkPlaceAndReturnsTokens() throws Exception {
        mockMvc.perform(post("/api/auth/signup/owner")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "provider": "KAKAO",
                                  "accessToken": "kakao-access-token",
                                  "name": "owner",
                                  "phoneNumber": "01000000000",
                                  "termsAgreements": [
                                    { "termsId": %d, "agreed": true },
                                    { "termsId": %d, "agreed": true },
                                    { "termsId": %d, "agreed": true }
                                  ],
                                  "workPlace": {
                                    "size": "FIVE_TO_NINE",
                                    "name": "store",
                                    "roadAddress": "road address",
                                    "detailAddress": "3F"
                                  },
                                  "device": {
                                    "deviceId": "device-1",
                                    "platform": "IOS",
                                    "appVersion": "1.0.0"
                                  }
                                }
                                """.formatted(commonAgeTermsId, commonUseTermsId, ownerPrivacyTermsId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("LOGIN_SUCCESS"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.member.role").value("OWNER"));

        assertThat(memberRepository.count()).isEqualTo(1);
        assertThat(workPlaceRepository.count()).isEqualTo(1);
    }

    /**
     * 사장 회원가입에서 사장 필수 약관에 동의하지 않으면 회원과 사업장을 만들지 않는다.
     */
    @Test
    void ownerSignupRejectsMissingOwnerRequiredTerms() throws Exception {
        mockMvc.perform(post("/api/auth/signup/owner")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "provider": "KAKAO",
                                  "accessToken": "kakao-access-token",
                                  "name": "owner",
                                  "phoneNumber": "01000000000",
                                  "termsAgreements": [
                                    { "termsId": %d, "agreed": true },
                                    { "termsId": %d, "agreed": true }
                                  ],
                                  "workPlace": {
                                    "size": "FIVE_TO_NINE",
                                    "name": "store",
                                    "roadAddress": "road address",
                                    "detailAddress": "3F"
                                  },
                                  "device": {
                                    "deviceId": "device-owner-invalid",
                                    "platform": "IOS",
                                    "appVersion": "1.0.0"
                                  }
                                }
                                """.formatted(commonAgeTermsId, commonUseTermsId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("4001"));

        assertThat(memberRepository.count()).isZero();
        assertThat(workPlaceRepository.count()).isZero();
    }

    /**
     * 근무자 회원가입은 사업장을 만들지 않고 회원만 만든 뒤 즉시 로그인 토큰을 반환한다.
     */
    @Test
    void workerSignupCreatesWorkerWithoutWorkPlaceAndReturnsTokens() throws Exception {
        mockMvc.perform(post("/api/auth/signup/worker")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(workerSignupBody("device-2")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("LOGIN_SUCCESS"))
                .andExpect(jsonPath("$.member.role").value("WORKER"));

        assertThat(memberRepository.count()).isEqualTo(1);
        assertThat(workPlaceRepository.count()).isZero();
    }

    /**
     * 동일한 소셜 계정으로 회원가입을 다시 시도하면 중복 가입으로 거절한다.
     */
    @Test
    void workerSignupRejectsDuplicateSocialAccount() throws Exception {
        signupWorkerAndReadResponse("device-first");

        mockMvc.perform(post("/api/auth/signup/worker")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(workerSignupBody("device-second")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("4005"));

        assertThat(memberRepository.count()).isEqualTo(1);
    }

    /**
     * refresh token은 저장된 기기 세션과 일치할 때 access token과 refresh token으로 교체된다.
     */
    @Test
    void refreshTokenRotatesDeviceRefreshToken() throws Exception {
        String refreshToken = signupWorkerAndReadResponse("device-3").get("refreshToken").asText();

        refreshToken(refreshToken, "device-3")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("LOGIN_SUCCESS"))
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty());
    }

    /**
     * 회원가입 성공 시 refresh token 원문이 아닌 hash가 Redis에 저장되고 RDB refresh_token 테이블은 생성되지 않는다.
     */
    @Test
    void signupStoresRefreshTokenHashInRedisWithoutRefreshTokenTable() throws Exception {
        JsonNode response = signupWorkerAndReadResponse("redis-device");
        Long memberId = response.get("member").get("memberId").asLong();
        String refreshToken = response.get("refreshToken").asText();

        assertThat(redisTemplate.opsForValue().get("auth:refresh-token:" + memberId + ":redis-device"))
                .isEqualTo(tokenHashService.hash(refreshToken));
        assertThat(refreshTokenTableExists()).isFalse();
    }

    /**
     * refresh token은 발급된 deviceId와 다른 기기 ID로 사용할 수 없다.
     */
    @Test
    void refreshTokenRejectsDifferentDeviceId() throws Exception {
        String refreshToken = signupWorkerAndReadResponse("device-owner").get("refreshToken").asText();

        refreshToken(refreshToken, "device-other")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("4002"));
    }

    /**
     * 한 번 회전된 refresh token은 재사용할 수 없다.
     */
    @Test
    void refreshTokenRejectsAlreadyRotatedToken() throws Exception {
        String oldRefreshToken = signupWorkerAndReadResponse("device-rotate").get("refreshToken").asText();

        refreshToken(oldRefreshToken, "device-rotate")
                .andExpect(status().isOk());

        refreshToken(oldRefreshToken, "device-rotate")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("4002"));
    }

    /**
     * 같은 회원이 여러 기기에서 로그인하면 각 deviceId별 refresh token 세션을 독립적으로 유지한다.
     */
    @Test
    void socialLoginKeepsRefreshTokenSessionsPerDevice() throws Exception {
        String iphoneRefreshToken = signupWorkerAndReadResponse("iphone").get("refreshToken").asText();
        String ipadRefreshToken = socialLoginAndReadResponse("ipad").get("refreshToken").asText();

        assertThat(iphoneRefreshToken).isNotEqualTo(ipadRefreshToken);

        refreshToken(iphoneRefreshToken, "iphone")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("LOGIN_SUCCESS"));
        refreshToken(ipadRefreshToken, "ipad")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("LOGIN_SUCCESS"));
    }

    /**
     * 외부 네트워크 호출 없이 소셜 인증 성공 결과를 반환하는 테스트용 전략이다.
     */
    /**
     * 로그아웃은 현재 기기의 refresh token 세션을 Redis에서 삭제한다.
     */
    @Test
    void logoutDeletesCurrentDeviceRefreshTokenSession() throws Exception {
        String refreshToken = signupWorkerAndReadResponse("logout-device").get("refreshToken").asText();

        logout(refreshToken, "logout-device")
                .andExpect(status().isNoContent());

        refreshToken(refreshToken, "logout-device")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("4002"));
    }

    /**
     * 이미 회전된 refresh token으로 로그아웃하면 현재 기기의 새 refresh token 세션을 삭제하지 않는다.
     */
    @Test
    void logoutRejectsAlreadyRotatedRefreshTokenWithoutDeletingCurrentSession() throws Exception {
        String oldRefreshToken = signupWorkerAndReadResponse("logout-rotate-device").get("refreshToken").asText();
        String newRefreshToken = extractRefreshToken(
                refreshToken(oldRefreshToken, "logout-rotate-device")
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString()
        );

        logout(oldRefreshToken, "logout-rotate-device")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("4002"));

        refreshToken(newRefreshToken, "logout-rotate-device")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("LOGIN_SUCCESS"));
    }

    private record StubSocialAuthProvider(
            SocialProvider provider,
            SocialUserInfo socialUserInfo
    ) implements SocialAuthProvider {

        /**
         * 테스트 전략이 담당하는 소셜 제공자를 반환한다.
         */
        @Override
        public SocialProvider supports() {
            return provider;
        }

        /**
         * 입력 토큰 검증은 서비스에서 수행하고, 테스트에서는 지정된 소셜 사용자 정보만 반환한다.
         */
        @Override
        public SocialUserInfo authenticate(SocialAuthCommand command) {
            return socialUserInfo;
        }
    }

    /**
     * 테스트용 근무자 회원가입을 수행하고 JSON 응답을 반환한다.
     */
    private JsonNode signupWorkerAndReadResponse(String deviceId) throws Exception {
        String response = mockMvc.perform(post("/api/auth/signup/worker")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(workerSignupBody(deviceId)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(response);
    }

    /**
     * 기존 회원의 소셜 로그인을 수행하고 JSON 응답을 반환한다.
     */
    private JsonNode socialLoginAndReadResponse(String deviceId) throws Exception {
        String response = mockMvc.perform(post("/api/auth/social-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "provider": "GOOGLE",
                                  "idToken": "google-id-token",
                                  "device": {
                                    "deviceId": "%s",
                                    "platform": "ANDROID",
                                    "appVersion": "1.0.0"
                                  }
                                }
                                """.formatted(deviceId)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(response);
    }

    /**
     * 근무자 회원가입 요청 JSON을 생성한다.
     */
    private String workerSignupBody(String deviceId) {
        return """
                {
                  "provider": "GOOGLE",
                  "idToken": "google-id-token",
                  "name": "worker",
                  "phoneNumber": "01000000000",
                  "termsAgreements": [
                    { "termsId": %d, "agreed": true },
                    { "termsId": %d, "agreed": true }
                  ],
                  "device": {
                    "deviceId": "%s",
                    "platform": "ANDROID",
                    "appVersion": "1.0.0"
                  }
                }
                """.formatted(commonAgeTermsId, commonUseTermsId, deviceId);
    }

    /**
     * refresh token 재발급 요청을 수행한다.
     */
    private ResultActions refreshToken(String refreshToken, String deviceId) throws Exception {
        return mockMvc.perform(post("/api/auth/token/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "refreshToken": "%s",
                          "deviceId": "%s"
                        }
                        """.formatted(refreshToken, deviceId)));
    }

    /**
     * Hibernate가 RDB에 refresh_token 테이블을 만들었는지 확인한다.
     */
    /**
     * 로그아웃 요청을 수행한다.
     */
    private ResultActions logout(String refreshToken, String deviceId) throws Exception {
        return mockMvc.perform(post("/api/auth/logout")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "refreshToken": "%s",
                          "deviceId": "%s"
                        }
                        """.formatted(refreshToken, deviceId)));
    }

    /**
     * 인증 응답에서 refresh token 값을 추출한다.
     */
    private String extractRefreshToken(String response) throws Exception {
        return objectMapper.readTree(response).get("refreshToken").asText();
    }

    private boolean refreshTokenTableExists() {
        Integer count = jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*)
                        FROM information_schema.tables
                        WHERE table_schema = DATABASE()
                          AND table_name = 'refresh_token'
                        """,
                Integer.class
        );
        return count != null && count > 0;
    }

    /**
     * 테스트 간 DB 상태가 섞이지 않도록 관련 테이블을 직접 비운다.
     */
    private void cleanupDatabase() {
        jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 0");
        jdbcTemplate.update("DELETE FROM member_terms_agreement");
        jdbcTemplate.update("DELETE FROM crew");
        jdbcTemplate.update("DELETE FROM work_place");
        jdbcTemplate.update("DELETE FROM member");
        jdbcTemplate.update("DELETE FROM terms");
        jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 1");
    }
}
