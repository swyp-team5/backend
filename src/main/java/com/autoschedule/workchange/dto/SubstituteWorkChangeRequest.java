package com.autoschedule.workchange.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 대타 요청 생성에 필요한 값을 전달한다.
 */
public record SubstituteWorkChangeRequest(
        @NotNull(message = "대타 요청 대상 근무 배정 ID는 필수입니다.")
        Long requestAssignmentId,

        @NotNull(message = "대타를 요청받을 근무자 ID는 필수입니다.")
        Long targetMemberId,

        @NotBlank(message = "대타 요청 사유는 필수입니다.")
        @Size(max = 500, message = "대타 요청 사유는 500자 이하로 입력해 주세요.")
        String reason
) {
}
