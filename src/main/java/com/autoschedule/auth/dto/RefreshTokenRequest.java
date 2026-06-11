package com.autoschedule.auth.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * refresh token 재발급에 필요한 토큰 원문과 기기 ID를 받는다.
 */
public record RefreshTokenRequest(
        @NotBlank String refreshToken,
        @NotBlank String deviceId
) {
}
