package com.autoschedule.workerselect.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.List;

/**
 * 근무자가 선택한 불가능 근무 타임 제출 요청이다.
 */
public record WorkerSelectRequest(

        @NotNull(message = "weekScheduleId는 필수입니다.")
        @Positive(message = "weekScheduleId는 양수여야 합니다.")
        Long weekScheduleId,

        List<@NotNull(message = "timeDetailId는 null일 수 없습니다.")
        @Positive(message = "timeDetailId는 양수여야 합니다.") Long> timeDetails
) {
}