package com.autoschedule.workerselect.dto;

import com.autoschedule.schedulecondition.domain.TimeDetail;

import java.time.LocalDate;

/**
 * 근무자가 선택한 불가능 근무 타임 정보를 반환한다.
 */
public record WorkerSelectTimeDetailResponse(
        Long timeDetailId,
        String dayName,
        LocalDate date,
        Integer workPartNo,
        String timeName
) {

    /**
     * 타임 상세 정보를 응답 DTO로 변환한다.
     */
    public static WorkerSelectTimeDetailResponse from(TimeDetail timeDetail) {
        return new WorkerSelectTimeDetailResponse(
                timeDetail.getId(),
                timeDetail.getDay().getDayName().name(),
                timeDetail.getDay().getDate(),
                timeDetail.getWorkPartNo(),
                timeDetail.getTimeName()
        );
    }
}