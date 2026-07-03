package com.autoschedule.schedule.dto;

import com.autoschedule.schedule.domain.ConfirmedWeekSchedule;

/**
 * 확정 주간 스케줄 생성 결과를 클라이언트에 전달한다.
 */
public record ConfirmedWeekScheduleResponse(
        Long confirmedWeekScheduleId,
        Long workPlaceId,
        Long weekScheduleId,
        Integer selectedCandidateNo,
        Integer assignmentCount,
        String status
) {

    /**
     * 확정 주간 스케줄과 생성된 배정 수로 응답을 생성한다.
     */
    public static ConfirmedWeekScheduleResponse of(
            ConfirmedWeekSchedule confirmedWeekSchedule,
            int assignmentCount
    ) {
        return new ConfirmedWeekScheduleResponse(
                confirmedWeekSchedule.getId(),
                confirmedWeekSchedule.getWorkPlaceId(),
                confirmedWeekSchedule.getWeekSchedule().getId(),
                confirmedWeekSchedule.getSelectedCandidateNo(),
                assignmentCount,
                confirmedWeekSchedule.getStatus().name()
        );
    }
}
