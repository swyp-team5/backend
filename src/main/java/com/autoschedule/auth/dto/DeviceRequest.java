package com.autoschedule.auth.dto;

import com.autoschedule.auth.domain.DevicePlatform;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * refresh token을 기기별로 유지하기 위한 모바일 기기 정보를 받는다.
 */
public record DeviceRequest(
        @NotBlank String deviceId,
        @NotNull DevicePlatform platform,
        @NotBlank String appVersion
) {
}
