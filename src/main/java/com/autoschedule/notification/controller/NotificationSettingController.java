package com.autoschedule.notification.controller;

import com.autoschedule.auth.jwt.JwtAuthenticationPrincipal;
import com.autoschedule.notification.dto.NotificationSettingResponse;
import com.autoschedule.notification.dto.NotificationSettingUpdateRequest;
import com.autoschedule.notification.service.NotificationSettingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 회원 알림 수신 설정 API 요청을 처리한다.
 */
@RestController
@RequiredArgsConstructor
public class NotificationSettingController {

    private final NotificationSettingService notificationSettingService;

    /**
     * 현재 로그인한 회원의 알림 수신 설정을 조회한다.
     */
    @GetMapping("/api/members/me/notification-settings")
    public NotificationSettingResponse getMySetting(
            @AuthenticationPrincipal JwtAuthenticationPrincipal principal
    ) {
        return notificationSettingService.getMySetting(principal.memberId());
    }

    /**
     * 현재 로그인한 회원의 알림 수신 설정을 변경한다.
     */
    @PatchMapping("/api/members/me/notification-settings")
    public NotificationSettingResponse updateMySetting(
            @AuthenticationPrincipal JwtAuthenticationPrincipal principal,
            @Valid @RequestBody NotificationSettingUpdateRequest request
    ) {
        return notificationSettingService.updateMySetting(principal.memberId(), request);
    }
}
