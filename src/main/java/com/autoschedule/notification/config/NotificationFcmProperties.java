package com.autoschedule.notification.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * FCM 발송 설정값을 표현한다.
 */
@ConfigurationProperties(prefix = "notification.fcm")
public record NotificationFcmProperties(
        boolean enabled,
        String credentialsPath
) {
}
