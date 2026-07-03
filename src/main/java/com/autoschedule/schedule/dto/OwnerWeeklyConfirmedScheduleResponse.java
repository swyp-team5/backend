package com.autoschedule.schedule.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * 사장용 사업장 주간 확정 근무표 응답이다.
 */
public record OwnerWeeklyConfirmedScheduleResponse(
        Long workPlaceId,
        LocalDate weekStartDate,
        LocalDate weekEndDate,
        List<OwnerWeeklyConfirmedScheduleDayResponse> days
) {

    /**
     * 사업장 주간 근무표 응답을 생성한다.
     */
    public static OwnerWeeklyConfirmedScheduleResponse of(
            Long workPlaceId,
            LocalDate weekStartDate,
            LocalDate weekEndDate,
            List<OwnerWeeklyConfirmedScheduleDayResponse> days
    ) {
        return new OwnerWeeklyConfirmedScheduleResponse(workPlaceId, weekStartDate, weekEndDate, days);
    }
}
