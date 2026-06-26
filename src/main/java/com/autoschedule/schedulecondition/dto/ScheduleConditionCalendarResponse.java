package com.autoschedule.schedulecondition.dto;

import com.autoschedule.schedulecondition.domain.WeekSchedule;

import java.time.LocalDate;
import java.util.List;

/**
 * 달력 활성화용 스케줄 조건 응답을 표현한다.
 */
public record ScheduleConditionCalendarResponse(
        Long weekScheduleId,
        Long workPlaceId,
        String weekScheduleName,
        LocalDate dueDate,
        List<AvailableDateResponse> availableDates
) {

    /**
     * 주간 스케줄 엔티티와 날짜 목록을 달력 활성화 응답으로 변환한다.
     */
    public static ScheduleConditionCalendarResponse from(
            WeekSchedule weekSchedule,
            List<AvailableDateResponse> availableDates
    ) {
        return new ScheduleConditionCalendarResponse(
                weekSchedule.getId(),
                weekSchedule.getWorkPlace().getId(),
                weekSchedule.getWeekScheduleName(),
                weekSchedule.getDueDate(),
                availableDates
        );
    }
}
