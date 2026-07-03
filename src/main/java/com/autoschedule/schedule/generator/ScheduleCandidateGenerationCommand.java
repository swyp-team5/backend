package com.autoschedule.schedule.generator;

import com.autoschedule.schedulecondition.domain.TimeDetail;
import com.autoschedule.schedulecondition.domain.WeekSchedule;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 자동 스케줄 후보 생성에 필요한 입력 조건이다.
 */
public record ScheduleCandidateGenerationCommand(
        WeekSchedule weekSchedule,
        List<TimeDetail> timeDetails,
        List<Long> workerMemberIds,
        Map<Long, Set<Long>> unavailableWorkerIdsByTimeDetailId
) {
}
