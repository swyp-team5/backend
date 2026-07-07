package com.autoschedule.schedule.generator;

import java.util.List;

/**
 * 자동 스케줄 후보 생성 결과와 알고리즘 버전을 함께 전달한다.
 */
public record ScheduleCandidateGenerationResult(
        String algorithmVersion,
        List<ScheduleCandidate> candidates,
        String failureReason
) {

    /**
     * 자동 스케줄 후보 생성 성공 결과를 생성한다.
     */
    public static ScheduleCandidateGenerationResult success(String algorithmVersion, List<ScheduleCandidate> candidates) {
        return new ScheduleCandidateGenerationResult(algorithmVersion, candidates, null);
    }

    /**
     * 자동 스케줄 후보를 만들 수 없는 원인을 포함한 실패 결과를 생성한다.
     */
    public static ScheduleCandidateGenerationResult failure(String algorithmVersion, String failureReason) {
        return new ScheduleCandidateGenerationResult(algorithmVersion, List.of(), failureReason);
    }
}
