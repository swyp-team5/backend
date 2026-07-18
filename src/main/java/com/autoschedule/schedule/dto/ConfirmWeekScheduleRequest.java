package com.autoschedule.schedule.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * 자동 생성 미리보기 후보 중 하나를 확정하기 위한 요청 값이다.
 */
public record ConfirmWeekScheduleRequest(
        @NotNull(message = "자동 스케줄 생성 이력 ID는 필수입니다.")
        @Positive(message = "자동 스케줄 생성 이력 ID는 1 이상의 값이어야 합니다.")
        Long scheduleGenerationRunId,

        @NotNull(message = "스케줄 미리보기 ID는 필수입니다.")
        @Positive(message = "스케줄 미리보기 ID는 1 이상의 값이어야 합니다.")
        Long schedulePreviewId,

        @NotNull(message = "확정할 후보 번호는 필수입니다.")
        @Positive(message = "확정할 후보 번호는 1 이상의 값이어야 합니다.")
        Integer selectedCandidateNo
) {
}
