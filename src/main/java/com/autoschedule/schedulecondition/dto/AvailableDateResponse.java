package com.autoschedule.schedulecondition.dto;

import com.autoschedule.schedulecondition.domain.Day;
import com.autoschedule.schedulecondition.domain.ScheduleDayName;

import java.time.LocalDate;

/**
 * 달력에 표시할 일자별 선택 상태 응답을 표현한다.
 */
public record AvailableDateResponse(
        LocalDate date,
        ScheduleDayName dayName,
        boolean holidayStatus,
        boolean selectLimitStatus
) {

    /**
     * 요일 조건 엔티티를 달력 일자 응답으로 변환한다.
     */
    public static AvailableDateResponse from(Day day) {
        return new AvailableDateResponse(
                day.getDate(),
                day.getDayName(),
                day.isHolidayStatus(),
                day.isSelectLimitStatus()
        );
    }
}
