package com.autoschedule.schedule.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * 교대/대타 요청 대상 선택을 위한 확정 근무 스케줄 응답이다.
 */
public record WorkChangeTargetScheduleResponse(
        Long workPlaceId,
        LocalDate fromDate,
        LocalDate toDate,
        List<OwnerWeeklyConfirmedScheduleDayResponse> days
) {

    /**
     * 조회 기간과 일자별 확정 근무 목록으로 교대/대타 대상 응답을 만든다.
     */
    public static WorkChangeTargetScheduleResponse of(
            Long workPlaceId,
            LocalDate fromDate,
            LocalDate toDate,
            List<OwnerWeeklyConfirmedScheduleDayResponse> days
    ) {
        return new WorkChangeTargetScheduleResponse(workPlaceId, fromDate, toDate, days);
    }
}
