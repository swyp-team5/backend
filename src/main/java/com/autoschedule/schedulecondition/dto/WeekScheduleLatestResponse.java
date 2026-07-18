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
        String nextWeekScheduleName, // 다음주차 이름 적용 변수
        LocalDate dueDate,
        List<ScheduleConditionGroupResponse> groups
) {

    public static WeekScheduleLatestResponse from(
            WeekSchedule weekSchedule,
            String nextWeekScheduleName,
            List<ScheduleConditionGroupResponse> groups
    ) {
        return new WeekScheduleLatestResponse(
                weekSchedule.getId(),
                weekSchedule.getWorkPlace().getId(),
                weekSchedule.getWeekScheduleName(),
                nextWeekScheduleName, // 다음주차 이름 적용 변수
                weekSchedule.getDueDate(),
                groups
        );
    }
}
