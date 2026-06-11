package com.autoschedule.global.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.autoschedule.auth.jwt.IssuedTokens;
import com.autoschedule.auth.jwt.JwtTokenProvider;
import com.autoschedule.global.security.annotation.OwnerOnly;
import com.autoschedule.global.security.annotation.WorkerOnly;
import com.autoschedule.member.domain.Member;
import com.autoschedule.member.domain.MemberRole;
import com.autoschedule.member.domain.SocialProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 컨트롤러 메서드/클래스 권한 애너테이션이 OWNER와 WORKER 권한을 올바르게 제한하는지 검증한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(RoleAuthorizationAnnotationTest.TestControllerConfig.class)
class RoleAuthorizationAnnotationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    /**
     * OWNER 토큰은 OWNER 전용 메서드에 접근할 수 있다.
     */
    @Test
    void ownerCanAccessOwnerOnlyMethod() throws Exception {
        mockMvc.perform(get("/test/security/owner")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(MemberRole.OWNER)))
                .andExpect(status().isOk());
    }

    /**
     * WORKER 토큰은 OWNER 전용 메서드에 접근할 수 없다.
     */
    @Test
    void workerCannotAccessOwnerOnlyMethod() throws Exception {
        mockMvc.perform(get("/test/security/owner")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(MemberRole.WORKER)))
                .andExpect(status().isForbidden());
    }

    /**
     * WORKER 토큰은 WORKER 전용 클래스의 모든 메서드에 접근할 수 있다.
     */
    @Test
    void workerCanAccessWorkerOnlyClass() throws Exception {
        mockMvc.perform(get("/test/security/worker")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(MemberRole.WORKER)))
                .andExpect(status().isOk());
    }

    /**
     * 인증 토큰이 없으면 권한 애너테이션이 붙은 API에 접근할 수 없다.
     */
    @Test
    void anonymousCannotAccessSecuredMethod() throws Exception {
        mockMvc.perform(get("/test/security/owner"))
                .andExpect(status().isUnauthorized());
    }

    /**
     * 테스트 권한에 맞는 access token을 발급한다.
     */
    private String bearerToken(MemberRole role) {
        Member member = Member.create(
                SocialProvider.GOOGLE,
                "subject-" + role.name(),
                role.name().toLowerCase() + "@test.com",
                role.name().toLowerCase(),
                "01000000000",
                role
        );
        ReflectionTestUtils.setField(member, "id", role == MemberRole.OWNER ? 1L : 2L);
        IssuedTokens tokens = jwtTokenProvider.issue(member);
        return "Bearer " + tokens.accessToken();
    }

    /**
     * 권한 애너테이션 검증에만 사용하는 테스트 컨트롤러를 등록한다.
     */
    @TestConfiguration
    static class TestControllerConfig {

        /**
         * 테스트용 OWNER 컨트롤러를 빈으로 등록한다.
         */
        @RestController
        @RequestMapping("/test/security")
        static class TestSecurityController {

            /**
             * 메서드 단위 OWNER 권한 검증을 확인한다.
             */
            @OwnerOnly
            @GetMapping("/owner")
            String owner() {
                return "owner";
            }
        }

        /**
         * 클래스 단위 WORKER 권한 검증을 확인한다.
         */
        @WorkerOnly
        @RestController
        @RequestMapping("/test/security/worker")
        static class TestWorkerController {

            /**
             * 클래스 단위 WORKER 권한이 메서드에 적용되는지 확인한다.
             */
            @GetMapping
            String worker() {
                return "worker";
            }
        }
    }
}
