package com.autoschedule.schedule.domain;

/**
 * 자동 스케줄 생성 실행 이력의 처리 상태를 정의한다.
 */
public enum ScheduleGenerationRunStatus {
    GENERATED,
    FAILED,
    DELETED
}
