package com.autoschedule.schedule.dto;

/**
 * 확정 스케줄 단건 근무 파트 삭제 결과를 전달한다.
 */
public record ManualScheduleAssignmentDeleteResponse(
        Long confirmedWeekScheduleId,
        Long timeDetailId,
        Integer deletedAssignmentCount,
        String status
) {
}
