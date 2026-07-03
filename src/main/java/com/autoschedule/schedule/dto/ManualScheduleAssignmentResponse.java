package com.autoschedule.schedule.dto;

import com.autoschedule.schedulecondition.domain.TimeDetail;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * 확정 스케줄 단건 근무 슬롯 추가/수정 결과를 전달한다.
 */
public record ManualScheduleAssignmentResponse(
        Long confirmedWeekScheduleId,
        Long workPlaceId,
        Long weekScheduleId,
        Long dayId,
        Long timeDetailId,
        LocalDate workDate,
        Integer workPartNo,
        String timeName,
        LocalTime startTime,
        LocalTime closeTime,
        Integer restTime,
        List<Long> workerMemberIds,
        Integer assignmentCount
) {

    /**
     * 생성된 time_detail과 근무자 배정 목록으로 응답을 만든다.
     */
    public static ManualScheduleAssignmentResponse of(
            Long confirmedWeekScheduleId,
            Long workPlaceId,
            Long weekScheduleId,
            TimeDetail timeDetail,
            List<Long> workerMemberIds
    ) {
        return new ManualScheduleAssignmentResponse(
                confirmedWeekScheduleId,
                workPlaceId,
                weekScheduleId,
                timeDetail.getDay().getId(),
                timeDetail.getId(),
                timeDetail.getDay().getDate(),
                timeDetail.getWorkPartNo(),
                timeDetail.getTimeName(),
                timeDetail.getStartTime(),
                timeDetail.getCloseTime(),
                timeDetail.getRestTime(),
                workerMemberIds,
                workerMemberIds.size()
        );
    }
}
