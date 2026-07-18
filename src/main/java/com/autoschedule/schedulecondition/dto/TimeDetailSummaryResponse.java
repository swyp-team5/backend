package com.autoschedule.schedulecondition.dto;

import com.autoschedule.schedulecondition.domain.TimeDetail;
import java.time.LocalTime;

/**
 * 타임 상세 요약 응답을 표현한다.
 */
public record TimeDetailSummaryResponse(
        Long timeDetailId,
        String timeName,
        LocalTime startTime,
        LocalTime closeTime,
        Integer workerCount
) {

    /**
     * 타임 상세 엔티티를 요약 응답으로 변환한다.
     */
    public static TimeDetailSummaryResponse from(TimeDetail timeDetail) {
        return new TimeDetailSummaryResponse(
                timeDetail.getId(),
                timeDetail.getTimeName(),
                timeDetail.getStartTime(),
                timeDetail.getCloseTime(),
                timeDetail.getWorkerCount()
        );
    }
}