package com.autoschedule.auth.social;

import com.autoschedule.member.domain.SocialProvider;

/**
 * 외부 소셜 인증 검증 후 우리 서비스가 사용하는 표준 사용자 식별 정보다.
 */
public record SocialUserInfo(
        SocialProvider provider,
        String subject,
        String email
) {
}
