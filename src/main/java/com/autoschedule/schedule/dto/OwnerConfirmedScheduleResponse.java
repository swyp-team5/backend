package com.autoschedule.schedule.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * 사장용 사업장 기간 확정 근무표 응답이다.
 */
public record OwnerConfirmedScheduleResponse(
        Long workPlaceId,
        LocalDate from,
        LocalDate to,
        List<OwnerWeeklyConfirmedScheduleDayResponse> days
) {

    /**
     * 사업장 기간 근무표 응답을 생성한다.
     */
    public static OwnerConfirmedScheduleResponse of(
            Long workPlaceId,
            LocalDate from,
            LocalDate to,
            List<OwnerWeeklyConfirmedScheduleDayResponse> days
    ) {
        return new OwnerConfirmedScheduleResponse(workPlaceId, from, to, days);
    }
}
