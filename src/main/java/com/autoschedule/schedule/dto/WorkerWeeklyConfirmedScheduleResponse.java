package com.autoschedule.schedule.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * 근무자가 교대/대타 대상을 선택하기 위해 조회하는 사업장 주간 확정 스케줄 응답이다.
 */
public record WorkerWeeklyConfirmedScheduleResponse(
        Long workPlaceId,
        LocalDate weekStartDate,
        LocalDate weekEndDate,
        List<OwnerWeeklyConfirmedScheduleDayResponse> days
) {

    /**
     * 사업장 주간 확정 스케줄 정보를 근무자용 응답으로 생성한다.
     */
    public static WorkerWeeklyConfirmedScheduleResponse of(
            Long workPlaceId,
            LocalDate weekStartDate,
            LocalDate weekEndDate,
            List<OwnerWeeklyConfirmedScheduleDayResponse> days
    ) {
        return new WorkerWeeklyConfirmedScheduleResponse(workPlaceId, weekStartDate, weekEndDate, days);
    }
}
