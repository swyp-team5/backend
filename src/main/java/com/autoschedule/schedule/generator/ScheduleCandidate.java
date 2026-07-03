package com.autoschedule.schedule.generator;

import java.util.List;

/**
 * 자동 스케줄 후보 1건의 JSON 직렬화 모델이다.
 */
public record ScheduleCandidate(
        int candidateNo,
        int score,
        List<ScheduleCandidateDay> days
) {

    /**
     * 후보 번호를 부여한 새 후보를 반환한다.
     */
    public ScheduleCandidate withCandidateNo(int candidateNo) {
        return new ScheduleCandidate(candidateNo, score, days);
    }
}
