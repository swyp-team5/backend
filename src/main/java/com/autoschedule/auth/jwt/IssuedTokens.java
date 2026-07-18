package com.autoschedule.auth.jwt;

/**
 * 발급된 access token과 refresh token 및 만료 시간을 함께 전달한다.
 */
public record IssuedTokens(
        String accessToken,
        String refreshToken,
        long accessTokenExpiresIn,
        long refreshTokenExpiresIn
) {
}
