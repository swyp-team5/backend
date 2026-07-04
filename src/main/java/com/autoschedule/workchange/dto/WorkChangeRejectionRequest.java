package com.autoschedule.workchange.dto;

import jakarta.validation.constraints.Size;

/**
 * 교대/대타 요청 거절 사유를 전달한다.
 */
public record WorkChangeRejectionRequest(
        @Size(max = 500, message = "거절 사유는 500자 이하로 입력해 주세요.")
        String reason
) {
}
