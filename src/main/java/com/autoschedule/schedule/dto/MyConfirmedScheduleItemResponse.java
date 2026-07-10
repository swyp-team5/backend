package com.autoschedule.schedule.dto;

import com.autoschedule.schedule.domain.ConfirmedScheduleAssignment;
import com.autoschedule.workplace.domain.WorkPlace;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * 근무자 본인 달력에 표시할 확정 근무 일정 단건 응답이다.
 */
public record MyConfirmedScheduleItemResponse(
        Long assignmentId,
        Long workPlaceId,
        String workPlaceName,
        LocalDate workDate,
        String dayName,
        Long timeDetailId,
        String timeName,
        Integer workPartNo,
        LocalTime startTime,
        LocalTime closeTime,
        Integer restTime
) {

    /**
     * 확정 근무 배정과 사업장 정보를 근무자 달력 응답 단건으로 변환한다.
     */
    public static MyConfirmedScheduleItemResponse from(
            ConfirmedScheduleAssignment assignment,
            WorkPlace workPlace
    ) {
        return new MyConfirmedScheduleItemResponse(
                assignment.getId(),
                assignment.getWorkPlaceId(),
                workPlace == null ? null : workPlace.getName(),
                assignment.getDay().getDate(),
                assignment.getDay().getDayName().name(),
                assignment.getTimeDetail().getId(),
                assignment.getTimeDetail().getTimeName(),
                assignment.getTimeDetail().getWorkPartNo(),
                assignment.getTimeDetail().getStartTime(),
                assignment.getTimeDetail().getCloseTime(),
                assignment.getTimeDetail().getRestTime()
        );
    }
}
