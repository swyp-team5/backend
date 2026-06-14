package com.autoschedule.notification.dto;

import com.autoschedule.notification.domain.NotificationType;
import com.autoschedule.notification.domain.PushPolicy;
import java.util.Map;

/**
 * 도메인 기능에서 회원에게 알림을 보낼 때 사용하는 내부 커맨드를 표현한다.
 */
public record NotificationSendCommand(
        NotificationType notificationType,
        PushPolicy pushPolicy,
        String title,
        String body,
        Map<String, String> data
) {
}
