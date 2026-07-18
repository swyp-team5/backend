package com.autoschedule.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * refresh token 재발급에 필요한 토큰 원문과 기기 ID를 받는다.
 */
public record RefreshTokenRequest(
        @NotBlank(message = "리프레시 토큰은 필수입니다.") String refreshToken,
        @NotBlank(message = "기기 ID는 필수입니다.")
        @Size(max = 100, message = "기기 ID는 100자 이하로 입력해주세요.")
        String deviceId
) {
}
