package com.autoschedule.notification.domain;

/**
 * 알림을 앱 내 알림함에만 저장할지 FCM 푸시까지 시도할지 정의한다.
 */
public enum PushPolicy {
    IN_APP_ONLY,
    PUSH
}
