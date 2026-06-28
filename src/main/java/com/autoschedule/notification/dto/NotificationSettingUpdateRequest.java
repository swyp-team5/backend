package com.autoschedule.notification.dto;

import jakarta.validation.constraints.NotNull;

/**
 * 회원 알림 수신 설정 변경 요청을 표현한다.
 */
public record NotificationSettingUpdateRequest(
        @NotNull(message = "FCM 푸시 수신 여부는 필수입니다.")
        Boolean fcmPushEnabled
) {
}
