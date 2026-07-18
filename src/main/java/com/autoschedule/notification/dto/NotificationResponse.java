package com.autoschedule.notification.dto;

import com.autoschedule.notification.domain.Notification;
import com.autoschedule.notification.domain.NotificationType;
import com.autoschedule.notification.domain.PushPolicy;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDateTime;

/**
 * 알림 단건 응답을 표현한다.
 */
public record NotificationResponse(
        Long notificationId,
        NotificationType notificationType,
        PushPolicy pushPolicy,
        String title,
        String body,
        JsonNode data,
        boolean read,
        LocalDateTime readAt,
        LocalDateTime createdAt
) {

    /**
     * 알림 엔티티와 파싱된 data를 모바일 응답으로 변환한다.
     */
    public static NotificationResponse from(Notification notification, JsonNode data) {
        return new NotificationResponse(
                notification.getId(),
                notification.getNotificationType(),
                notification.getPushPolicy(),
                notification.getTitle(),
                notification.getBody(),
                data,
                notification.getReadAt() != null,
                notification.getReadAt(),
                notification.getCreatedAt()
        );
    }
}
