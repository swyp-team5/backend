package com.autoschedule.workchange.domain;

/**
 * 근무 변경 요청의 처리 흐름 상태를 표현한다.
 */
public enum WorkChangeRequestStatus {
    REQUESTED,
    ACCEPTED_BY_TARGET,
    REJECTED_BY_TARGET,
    APPROVED,
    REJECTED_BY_OWNER,
    CANCELED
}
