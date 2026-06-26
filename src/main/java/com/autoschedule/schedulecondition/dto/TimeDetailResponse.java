package com.autoschedule.schedulecondition.dto;

import com.autoschedule.schedulecondition.domain.TimeDetail;
import java.time.LocalTime;

/**
 * 타임별 상세 정보 조회 응답을 표현한다.
 */
public record TimeDetailResponse(
        Long timeDetailId,
        Integer workPartNo,
        String timeName,
        Integer workerCount,
        LocalTime startTime,
        LocalTime closeTime,
        Integer restTime
) {

    public static TimeDetailResponse from(TimeDetail timeDetail) {
        return new TimeDetailResponse(
                timeDetail.getId(),
                timeDetail.getWorkPartNo(),
                timeDetail.getTimeName(),
                timeDetail.getWorkerCount(),
                timeDetail.getStartTime(),
                timeDetail.getCloseTime(),
                timeDetail.getRestTime()
        );
    }
}