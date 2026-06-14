package com.autoschedule.notification.controller;

import com.autoschedule.auth.jwt.JwtAuthenticationPrincipal;
import com.autoschedule.notification.dto.FcmTokenRegisterRequest;
import com.autoschedule.notification.dto.FcmTokenResponse;
import com.autoschedule.notification.service.FcmTokenService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * FCM 토큰 등록과 비활성화 API 요청을 처리한다.
 */
@Validated
@RestController
@RequiredArgsConstructor
public class FcmTokenController {

    private final FcmTokenService fcmTokenService;

    /**
     * 현재 로그인한 회원의 기기별 FCM 토큰을 등록하거나 갱신한다.
     */
    @PostMapping("/api/fcm-tokens")
    public FcmTokenResponse register(
            @AuthenticationPrincipal JwtAuthenticationPrincipal principal,
            @Valid @RequestBody FcmTokenRegisterRequest request
    ) {
        return fcmTokenService.register(principal.memberId(), request);
    }

    /**
     * 현재 로그인한 회원의 특정 기기 FCM 토큰을 비활성화한다.
     */
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @DeleteMapping("/api/fcm-tokens/devices/{deviceId}")
    public void deactivate(
            @AuthenticationPrincipal JwtAuthenticationPrincipal principal,
            @PathVariable @NotBlank(message = "기기 ID는 필수입니다.") String deviceId
    ) {
        fcmTokenService.deactivate(principal.memberId(), deviceId);
    }
}
