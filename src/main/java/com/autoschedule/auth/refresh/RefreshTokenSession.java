package com.autoschedule.auth.refresh;

import java.time.Duration;

/**
 * Redis에 저장할 회원 기기별 refresh token 세션 값이다.
 */
public record RefreshTokenSession(
        Long memberId,
        String deviceId,
        String tokenHash,
        Duration ttl
) {
}
