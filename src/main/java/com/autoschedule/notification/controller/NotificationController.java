package com.autoschedule.notification.controller;

import com.autoschedule.auth.jwt.JwtAuthenticationPrincipal;
import com.autoschedule.notification.dto.NotificationCursorResponse;
import com.autoschedule.notification.dto.NotificationResponse;
import com.autoschedule.notification.service.NotificationInboxService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 회원 알림함 API 요청을 처리한다.
 */
@Validated
@RestController
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationInboxService notificationInboxService;

    /**
     * 현재 로그인한 회원의 알림함을 최신순 커서 방식으로 조회한다.
     */
    @GetMapping("/api/notifications")
    public NotificationCursorResponse getNotifications(
            @AuthenticationPrincipal JwtAuthenticationPrincipal principal,
            @RequestParam(required = false) @Positive(message = "알림 커서는 1 이상이어야 합니다.") Long cursorId,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "조회 개수는 1 이상이어야 합니다.")
            @Max(value = 100, message = "조회 개수는 100 이하여야 합니다.") int size
    ) {
        return notificationInboxService.getNotifications(principal.memberId(), cursorId, size);
    }

    /**
     * 현재 로그인한 회원의 알림 단건을 읽음 처리한다.
     */
    @PatchMapping("/api/notifications/{notificationId}/read")
    public NotificationResponse markAsRead(
            @AuthenticationPrincipal JwtAuthenticationPrincipal principal,
            @PathVariable Long notificationId
    ) {
        return notificationInboxService.markAsRead(principal.memberId(), notificationId);
    }

    /**
     * 현재 로그인한 회원의 모든 미읽음 알림을 읽음 처리한다.
     */
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PatchMapping("/api/notifications/read-all")
    public void markAllAsRead(@AuthenticationPrincipal JwtAuthenticationPrincipal principal) {
        notificationInboxService.markAllAsRead(principal.memberId());
    }
}
