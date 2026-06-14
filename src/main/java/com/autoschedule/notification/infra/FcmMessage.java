package com.autoschedule.notification.infra;

import java.util.Map;

/**
 * FCM으로 전송할 메시지 정보를 표현한다.
 */
public record FcmMessage(
        String token,
        String title,
        String body,
        Map<String, String> data
) {
}
