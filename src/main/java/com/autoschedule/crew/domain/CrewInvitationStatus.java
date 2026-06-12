package com.autoschedule.crew.domain;

/**
 * 크루 초대 코드의 사용 가능 상태를 정의한다.
 */
public enum CrewInvitationStatus {
    ACTIVE,
    USED,
    EXPIRED,
    LOCKED,
    CANCELED
}
