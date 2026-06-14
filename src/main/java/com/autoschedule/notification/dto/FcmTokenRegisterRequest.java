package com.autoschedule.notification.dto;

import com.autoschedule.auth.domain.DevicePlatform;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * FCM 토큰 등록 또는 갱신 요청을 표현한다.
 */
public record FcmTokenRegisterRequest(
        @NotBlank(message = "기기 ID는 필수입니다.")
        @Size(max = 100, message = "기기 ID는 100자 이하여야 합니다.")
        String deviceId,

        @NotBlank(message = "FCM 토큰은 필수입니다.")
        @Size(max = 512, message = "FCM 토큰은 512자 이하여야 합니다.")
        String token,

        @NotNull(message = "기기 플랫폼은 필수입니다.")
        DevicePlatform platform,

        @NotBlank(message = "앱 버전은 필수입니다.")
        @Size(max = 30, message = "앱 버전은 30자 이하여야 합니다.")
        String appVersion
) {
}
