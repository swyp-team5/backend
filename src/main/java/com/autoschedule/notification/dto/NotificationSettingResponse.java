package com.autoschedule.notification.dto;

import com.autoschedule.notification.domain.MemberNotificationSetting;

/**
 * 회원 알림 수신 설정 응답을 표현한다.
 */
public record NotificationSettingResponse(
        boolean fcmPushEnabled
) {

    /**
     * 알림 수신 설정 엔티티를 API 응답으로 변환한다.
     */
    public static NotificationSettingResponse from(MemberNotificationSetting setting) {
        return new NotificationSettingResponse(setting.isFcmPushEnabled());
    }
}
