package com.autoschedule.schedule.generator;

import java.util.List;

/**
 * 자동 스케줄 후보 생성 결과와 알고리즘 버전을 함께 전달한다.
 */
public record ScheduleCandidateGenerationResult(
        String algorithmVersion,
        List<ScheduleCandidate> candidates
) {
}
