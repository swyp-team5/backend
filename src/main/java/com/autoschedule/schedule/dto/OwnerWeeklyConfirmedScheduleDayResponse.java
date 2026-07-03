package com.autoschedule.schedule.dto;

import com.autoschedule.schedulecondition.domain.Day;
import java.time.LocalDate;
import java.util.List;

/**
 * 사장 주간 근무표의 날짜별 응답이다.
 */
public record OwnerWeeklyConfirmedScheduleDayResponse(
        LocalDate workDate,
        String dayName,
        List<OwnerWeeklyConfirmedScheduleTimeDetailResponse> timeDetails
) {

    /**
     * 날짜 정보와 근무 타임 목록을 날짜별 응답으로 변환한다.
     */
    public static OwnerWeeklyConfirmedScheduleDayResponse from(
            Day day,
            List<OwnerWeeklyConfirmedScheduleTimeDetailResponse> timeDetails
    ) {
        return new OwnerWeeklyConfirmedScheduleDayResponse(
                day.getDate(),
                day.getDayName().name(),
                timeDetails
        );
    }
}
