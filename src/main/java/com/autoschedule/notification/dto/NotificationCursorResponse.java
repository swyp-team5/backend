package com.autoschedule.notification.dto;

import java.util.List;

/**
 * 알림함 커서 조회 응답을 표현한다.
 */
public record NotificationCursorResponse(
        List<NotificationResponse> content,
        Long nextCursorId,
        boolean hasNext
) {
}
