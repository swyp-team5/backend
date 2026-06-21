package com.autoschedule.schedulecondition.dto;

import com.autoschedule.schedulecondition.domain.Day;
import com.autoschedule.schedulecondition.domain.ScheduleDayName;
import com.autoschedule.schedulecondition.domain.TimeDetail;
import com.autoschedule.schedulecondition.domain.WeekSchedule;

import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;

/**
 * 그룹핑된 요일별 스케줄 조건 응답을 표현한다.
 */
public record ScheduleConditionGroupResponse(
        Integer groupingId,
        List<ScheduleDayName> dayNames,
        LocalTime workPlaceOpenTime,
        LocalTime workPlaceCloseTime,
        Integer minPersonalWorkCount,
        Integer maxPersonalWorkCount,
        List<TimeDetailResponse> timeDetails
) {

    /**
     * 같은 groupingId를 가진 요일 조건과 타임 상세 정보를 응답으로 변환한다.
     */
    public static ScheduleConditionGroupResponse from(
            Integer groupingId,
            WeekSchedule weekSchedule,
            List<Day> groupedDays,
            List<TimeDetail> timeDetails
    ) {
        Day representativeDay = groupedDays.stream()
                .min(Comparator.comparing(Day::getDate))
                .orElseThrow();

        List<ScheduleDayName> dayNames = groupedDays.stream()
                .sorted(Comparator.comparing(Day::getDate))
                .map(Day::getDayName)
                .toList();

        List<TimeDetailResponse> timeDetailResponses = timeDetails.stream()
                .sorted(Comparator.comparing(TimeDetail::getWorkPartNo))
                .map(TimeDetailResponse::from)
                .toList();

        return new ScheduleConditionGroupResponse(
                groupingId,
                dayNames,
                weekSchedule.getWorkPlaceOpenTime(),
                weekSchedule.getWorkPlaceCloseTime(),
                weekSchedule.getMinPersonalWorkCount(),
                weekSchedule.getMaxPersonalWorkCount(),
                timeDetailResponses
        );
    }
}