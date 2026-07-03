package com.autoschedule.schedule.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * 근무자 본인의 기간별 확정 근무 일정 목록 응답이다.
 */
public record MyConfirmedScheduleResponse(
        LocalDate from,
        LocalDate to,
        List<MyConfirmedScheduleItemResponse> schedules
) {

    /**
     * 근무자 달력 조회 응답을 생성한다.
     */
    public static MyConfirmedScheduleResponse of(
            LocalDate from,
            LocalDate to,
            List<MyConfirmedScheduleItemResponse> schedules
    ) {
        return new MyConfirmedScheduleResponse(from, to, schedules);
    }
}
