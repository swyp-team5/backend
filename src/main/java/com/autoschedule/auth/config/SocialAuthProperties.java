package com.autoschedule.auth.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 소셜 제공자별 토큰 검증에 필요한 설정값을 바인딩한다.
 */
@ConfigurationProperties(prefix = "auth.social")
public record SocialAuthProperties(
        Google google,
        Kakao kakao,
        Apple apple
) {

    /**
     * Google ID Token aud 검증에 사용할 클라이언트 ID 목록이다.
     */
    public record Google(List<String> audiences) {
    }

    /**
     * Kakao 사용자 정보 API 호출 설정이다.
     */
    public record Kakao(String userInfoUri) {
    }

    /**
     * Apple identity token과 authorization code 검증에 필요한 설정이다.
     */
    public record Apple(
            String clientId,
            String teamId,
            String keyId,
            String privateKey,
            String jwksUri,
            String tokenUri
    ) {
    }
}
