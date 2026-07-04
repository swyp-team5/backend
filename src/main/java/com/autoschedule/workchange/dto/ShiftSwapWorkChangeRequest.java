package com.autoschedule.workchange.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 교대 요청 생성에 필요한 값을 전달한다.
 */
public record ShiftSwapWorkChangeRequest(
        @NotNull(message = "교대 요청자의 근무 배정 ID는 필수입니다.")
        Long requestAssignmentId,

        @NotNull(message = "교대 대상 근무자의 근무 배정 ID는 필수입니다.")
        Long targetAssignmentId,

        @NotBlank(message = "교대 요청 사유는 필수입니다.")
        @Size(max = 500, message = "교대 요청 사유는 500자 이하로 입력해 주세요.")
        String reason
) {
}
