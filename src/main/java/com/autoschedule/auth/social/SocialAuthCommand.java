package com.autoschedule.auth.social;

import com.autoschedule.member.domain.SocialProvider;

/**
 * 소셜 제공자별 토큰 검증에 필요한 인증 입력값을 전달한다.
 */
public record SocialAuthCommand(
        SocialProvider provider,
        String idToken,
        String accessToken,
        String authorizationCode
) {
}
