package com.autoschedule.notification.dto;

/**
 * 테스트 푸시 요청으로 생성된 알림 식별자를 반환한다.
 */
public record NotificationTestPushResponse(
        Long notificationId
) {
}
