package com.autoschedule.schedule.generator;

import java.util.List;

/**
 * 자동 스케줄 후보의 일자별 배정 정보를 표현한다.
 */
public record ScheduleCandidateDay(
        Long dayId,
        List<ScheduleCandidateTimeDetail> timeDetails
) {
}
