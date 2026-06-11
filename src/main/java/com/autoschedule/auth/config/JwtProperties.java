package com.autoschedule.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 우리 서버 access token과 refresh token 발급 설정을 바인딩한다.
 */
@ConfigurationProperties(prefix = "auth.jwt")
public record JwtProperties(
        String secret,
        long accessTokenValiditySeconds,
        long refreshTokenValiditySeconds
) {
}
