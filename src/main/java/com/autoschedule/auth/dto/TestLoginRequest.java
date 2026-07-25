package com.autoschedule.auth.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * App Store 심사용 ID/PW 테스트 계정 로그인 요청이다.
 */
public record TestLoginRequest(
        @NotBlank(message = "아이디는 필수입니다.") String loginId,
        @NotBlank(message = "비밀번호는 필수입니다.") String password,
        @Valid @NotNull(message = "기기 정보는 필수입니다.") DeviceRequest device
) {
}
