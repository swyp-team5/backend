package com.autoschedule.schedule.generator;

/**
 * 자동 스케줄 후보 생성 알고리즘의 교체 지점이다.
 */
public interface ScheduleCandidateGenerator {

    /**
     * 주간 스케줄 조건과 근무자 제출 조건을 기준으로 확정 가능한 후보들을 생성한다.
     */
    ScheduleCandidateGenerationResult generate(ScheduleCandidateGenerationCommand command);
}
