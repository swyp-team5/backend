package com.autoschedule.schedule.dto;

import com.autoschedule.schedule.domain.ScheduleGenerationRun;
import com.autoschedule.schedule.domain.SchedulePreview;

/**
 * 자동 스케줄 생성 실행 결과를 클라이언트에 전달한다.
 */
public record ScheduleGenerationRunResponse(
        Long scheduleGenerationRunId,
        Long schedulePreviewId,
        Long workPlaceId,
        Long weekScheduleId,
        Integer candidateCount,
        String status
) {

    /**
     * 자동 생성 실행 이력과 미리보기 스냅샷으로 응답을 생성한다.
     */
    public static ScheduleGenerationRunResponse from(ScheduleGenerationRun run, SchedulePreview preview) {
        return new ScheduleGenerationRunResponse(
                run.getId(),
                preview.getId(),
                run.getWorkPlaceId(),
                run.getWeekSchedule().getId(),
                run.getTotalPreviewCount(),
                run.getStatus().name()
        );
    }
}
