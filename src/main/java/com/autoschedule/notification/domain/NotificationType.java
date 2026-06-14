package com.autoschedule.notification.domain;

/**
 * 회원에게 전달되는 알림의 업무 유형을 정의한다.
 */
public enum NotificationType {
    NOTICE,
    SCHEDULE_CONFIRMED,
    SCHEDULE_UPDATED,
    SHIFT_SWAP_REQUEST,
    SHIFT_SWAP_APPROVED,
    CREW_JOINED,
    CLOCK_IN_OUT
}
