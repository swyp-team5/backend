package com.autoschedule.schedule.dto;

import com.autoschedule.schedulecondition.domain.TimeDetail;
import java.time.LocalTime;
import java.util.List;

/**
 * 사장 주간 근무표의 근무 타임별 배정 응답이다.
 */
public record OwnerWeeklyConfirmedScheduleTimeDetailResponse(
        Long timeDetailId,
        String timeName,
        Integer workPartNo,
        LocalTime startTime,
        LocalTime closeTime,
        Integer restTime,
        List<ConfirmedScheduleWorkerResponse> workers
) {

    /**
     * 근무 타임 정보와 배정 근무자 목록을 응답으로 변환한다.
     */
    public static OwnerWeeklyConfirmedScheduleTimeDetailResponse from(
            TimeDetail timeDetail,
            List<ConfirmedScheduleWorkerResponse> workers
    ) {
        return new OwnerWeeklyConfirmedScheduleTimeDetailResponse(
                timeDetail.getId(),
                timeDetail.getTimeName(),
                timeDetail.getWorkPartNo(),
                timeDetail.getStartTime(),
                timeDetail.getCloseTime(),
                timeDetail.getRestTime(),
                workers
        );
    }
}
