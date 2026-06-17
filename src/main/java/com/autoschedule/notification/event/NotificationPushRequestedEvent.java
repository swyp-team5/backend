package com.autoschedule.notification.event;

import java.util.List;

/**
 * DB 커밋 이후 FCM 앱 푸시 발송을 요청하는 이벤트다.
 */
public record NotificationPushRequestedEvent(
        List<Long> deliveryIds
) {
}
