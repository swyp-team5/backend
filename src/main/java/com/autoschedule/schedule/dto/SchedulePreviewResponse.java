package com.autoschedule.schedule.dto;

import com.autoschedule.schedule.domain.ScheduleGenerationRun;
import com.autoschedule.schedule.domain.SchedulePreview;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * 자동 생성 미리보기 JSON 스냅샷을 클라이언트에 전달한다.
 */
public record SchedulePreviewResponse(
        Long scheduleGenerationRunId,
        Long schedulePreviewId,
        Long workPlaceId,
        Long weekScheduleId,
        Integer candidateCount,
        JsonNode previewData
) {

    /**
     * 자동 생성 실행 이력과 JSON 스냅샷으로 응답을 생성한다.
     */
    public static SchedulePreviewResponse of(
            ScheduleGenerationRun run,
            SchedulePreview preview,
            JsonNode previewData
    ) {
        return new SchedulePreviewResponse(
                run.getId(),
                preview.getId(),
                run.getWorkPlaceId(),
                run.getWeekSchedule().getId(),
                run.getTotalPreviewCount(),
                previewData
        );
    }
}
