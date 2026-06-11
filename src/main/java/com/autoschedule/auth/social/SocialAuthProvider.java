package com.autoschedule.auth.social;

import com.autoschedule.member.domain.SocialProvider;

/**
 * 소셜 제공자별 인증 검증 전략이 구현해야 하는 공통 계약이다.
 */
public interface SocialAuthProvider {

    /**
     * 이 전략이 담당하는 소셜 제공자를 반환한다.
     */
    SocialProvider supports();

    /**
     * 소셜 제공자의 토큰을 검증하고 표준 사용자 식별 정보를 반환한다.
     */
    SocialUserInfo authenticate(SocialAuthCommand command);
}
