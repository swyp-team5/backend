package com.autoschedule.workerselect.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * 근무자가 선택한 불가능 근무 타임 제출 요청이다.
 */
public record WorkerSelectRequest(

        @NotNull(message = "weekScheduleId는 필수입니다.")
        Long weekScheduleId,

        @NotNull(message = "timeDetails는 필수입니다.")
        List<Long> timeDetails
) {
}