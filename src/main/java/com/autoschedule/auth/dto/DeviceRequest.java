package com.autoschedule.auth.dto;

import com.autoschedule.auth.domain.DevicePlatform;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * refresh token을 기기별로 유지하기 위한 모바일 기기 정보를 받는다.
 */
public record DeviceRequest(
        @NotBlank(message = "기기 ID는 필수입니다.")
        @Size(max = 100, message = "기기 ID는 100자 이하로 입력해주세요.")
        String deviceId,
        @NotNull(message = "기기 플랫폼은 필수입니다.") DevicePlatform platform,
        @NotBlank(message = "앱 버전은 필수입니다.")
        @Size(max = 30, message = "앱 버전은 30자 이하로 입력해주세요.")
        String appVersion
) {
}
