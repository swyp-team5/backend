package com.autoschedule.auth.jwt;

import com.autoschedule.member.domain.MemberRole;

/**
 * JWT 검증 후 Spring Security 인증 객체에 담을 최소 회원 정보를 표현한다.
 */
public record JwtAuthenticationPrincipal(
        Long memberId,
        MemberRole role
) {
}
