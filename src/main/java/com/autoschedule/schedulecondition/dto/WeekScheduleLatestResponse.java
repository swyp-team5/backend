package com.autoschedule.schedulecondition.dto;

import com.autoschedule.schedulecondition.domain.WeekSchedule;
import java.time.LocalDate;
import java.util.List;

/**
 * 가장 최근 스케줄 조건 조회 응답을 표현한다.
 */
public record WeekScheduleLatestResponse(
        Long weekScheduleId,
        Long workPlaceId,
        String weekScheduleName,
        LocalDate dueDate,
        List<ScheduleConditionGroupResponse> groups
) {

    public static WeekScheduleLatestResponse from(
            WeekSchedule weekSchedule,
            List<ScheduleConditionGroupResponse> groups
    ) {
        return new WeekScheduleLatestResponse(
                weekSchedule.getId(),
                weekSchedule.getWorkPlace().getId(),
                weekSchedule.getWeekScheduleName(),
                weekSchedule.getDueDate(),
                groups
        );
    }
}