package com.autoschedule.schedulelogic.dto;

import java.util.List;

/**
 * 자동 스케줄링 실행 결과를 반환한다.
 */
public record ScheduleLogicResponse(
        Long workPlaceId,
        Long weekScheduleId,
        Long requestedByMemberId,
        Integer totalPreviewCount,
        List<ScheduleResultDto> schedules
) {
    public static ScheduleLogicResponse of(
            Long workPlaceId,
            Long weekScheduleId,
            Long requestedByMemberId,
            List<ScheduleResultDto> schedules
    ) {
        return new ScheduleLogicResponse(
                workPlaceId,
                weekScheduleId,
                requestedByMemberId,
                schedules.size(),
                schedules
        );
    }
}