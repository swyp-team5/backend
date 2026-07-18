package com.autoschedule.auth.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.autoschedule.auth.config.JwtProperties;
import com.autoschedule.global.exception.ApiException;
import com.autoschedule.member.domain.Member;
import com.autoschedule.member.domain.MemberRole;
import com.autoschedule.member.domain.SocialProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 서비스 JWT 발급과 검증 규칙을 Spring Context 없이 단위 테스트로 검증한다.
 */
class JwtTokenProviderTest {

    private JwtTokenProvider jwtTokenProvider;
    private Member member;

    /**
     * 테스트마다 고정된 설정과 회원을 준비한다.
     */
    @BeforeEach
    void setUp() {
        jwtTokenProvider = new JwtTokenProvider(new JwtProperties(
                "unit-test-secret-key-must-be-at-least-32-bytes",
                1800,
                1209600
        ));
        member = Member.create(
                SocialProvider.GOOGLE,
                "google-subject",
                "google@test.com",
                "worker",
                "01000000000",
                MemberRole.WORKER
        );
        ReflectionTestUtils.setField(member, "id", 1L);
    }

    /**
     * access token은 access token 검증에서 회원 ID와 역할을 반환한다.
     */
    @Test
    void validateAccessTokenReturnsPrincipal() {
        IssuedTokens tokens = jwtTokenProvider.issue(member);

        JwtAuthenticationPrincipal principal = jwtTokenProvider.validateAccessToken(tokens.accessToken());

        assertThat(principal.memberId()).isEqualTo(1L);
        assertThat(principal.role()).isEqualTo(MemberRole.WORKER);
    }

    /**
     * refresh token은 refresh token 검증에서 회원 ID를 반환한다.
     */
    @Test
    void validateRefreshTokenReturnsMemberId() {
        IssuedTokens tokens = jwtTokenProvider.issue(member);

        Long memberId = jwtTokenProvider.validateRefreshToken(tokens.refreshToken());

        assertThat(memberId).isEqualTo(1L);
    }

    /**
     * access token을 refresh token으로 사용할 수 없다.
     */
    @Test
    void validateRefreshTokenRejectsAccessToken() {
        IssuedTokens tokens = jwtTokenProvider.issue(member);

        assertThatThrownBy(() -> jwtTokenProvider.validateRefreshToken(tokens.accessToken()))
                .isInstanceOf(ApiException.class);
    }

    /**
     * refresh token을 access token으로 사용할 수 없다.
     */
    @Test
    void validateAccessTokenRejectsRefreshToken() {
        IssuedTokens tokens = jwtTokenProvider.issue(member);

        assertThatThrownBy(() -> jwtTokenProvider.validateAccessToken(tokens.refreshToken()))
                .isInstanceOf(ApiException.class);
    }

    /**
     * 서명 구조가 잘못된 토큰은 검증을 통과할 수 없다.
     */
    @Test
    void validateTokenRejectsMalformedToken() {
        assertThatThrownBy(() -> jwtTokenProvider.validateAccessToken("malformed-token"))
                .isInstanceOf(ApiException.class);
    }

    /**
     * 같은 회원에게 같은 초에 발급하더라도 refresh token은 매번 고유해야 한다.
     */
    @Test
    void issueCreatesUniqueRefreshTokens() {
        IssuedTokens firstTokens = jwtTokenProvider.issue(member);
        IssuedTokens secondTokens = jwtTokenProvider.issue(member);

        assertThat(firstTokens.refreshToken()).isNotEqualTo(secondTokens.refreshToken());
    }
}
