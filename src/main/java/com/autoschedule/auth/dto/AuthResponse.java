package com.autoschedule.auth.dto;

import com.autoschedule.auth.jwt.IssuedTokens;
import com.autoschedule.member.domain.Member;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 소셜 로그인과 회원가입 성공 여부를 앱에 알려주는 인증 API 표준 응답이다.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AuthResponse(
        AuthStatus status,
        String accessToken,
        String refreshToken,
        String tokenType,
        Long accessTokenExpiresIn,
        Long refreshTokenExpiresIn,
        MemberSummaryResponse member
) {

    /**
     * 기존 회원이 없어 회원가입 화면으로 이동해야 하는 응답을 생성한다.
     */
    public static AuthResponse signupRequired() {
        return new AuthResponse(AuthStatus.SIGNUP_REQUIRED, null, null, null, null, null, null);
    }

    /**
     * 로그인 성공 토큰과 회원 요약 정보를 포함한 응답을 생성한다.
     */
    public static AuthResponse loginSuccess(IssuedTokens tokens, Member member) {
        return new AuthResponse(
                AuthStatus.LOGIN_SUCCESS,
                tokens.accessToken(),
                tokens.refreshToken(),
                "Bearer",
                tokens.accessTokenExpiresIn(),
                tokens.refreshTokenExpiresIn(),
                MemberSummaryResponse.from(member)
        );
    }
}
