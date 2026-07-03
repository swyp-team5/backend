package com.autoschedule.schedule.generator;

import java.util.List;

/**
 * 자동 스케줄 후보의 시간대별 근무자 배정 정보를 표현한다.
 */
public record ScheduleCandidateTimeDetail(
        Long timeDetailId,
        List<Long> workerMemberIds
) {
}
