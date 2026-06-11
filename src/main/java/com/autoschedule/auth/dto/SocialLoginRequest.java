package com.autoschedule.auth.dto;

import com.autoschedule.member.domain.SocialProvider;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/**
 * 기존 회원 로그인 여부를 확인하기 위한 소셜 로그인 요청이다.
 */
public record SocialLoginRequest(
        @NotNull SocialProvider provider,
        String idToken,
        String accessToken,
        String authorizationCode,
        @Valid @NotNull DeviceRequest device
) {
}
