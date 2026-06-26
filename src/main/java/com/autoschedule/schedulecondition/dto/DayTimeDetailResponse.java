package com.autoschedule.schedulecondition.dto;

import com.autoschedule.schedulecondition.domain.Day;
import com.autoschedule.schedulecondition.domain.ScheduleDayName;
import java.time.LocalDate;
import java.util.List;

/**
 * 특정 일자의 타임 상세 조회 응답을 표현한다.
 */
public record DayTimeDetailResponse(
        Long weekScheduleId,
        LocalDate date,
        ScheduleDayName dayName,
        List<TimeDetailSummaryResponse> timeDetails
) {

    /**
     * 요일 조건 엔티티와 타임 상세 목록을 응답으로 변환한다.
     */
    public static DayTimeDetailResponse from(
            Day day,
            List<TimeDetailSummaryResponse> timeDetails
    ) {
        return new DayTimeDetailResponse(
                day.getWeekSchedule().getId(),
                day.getDate(),
                day.getDayName(),
                timeDetails
        );
    }
}