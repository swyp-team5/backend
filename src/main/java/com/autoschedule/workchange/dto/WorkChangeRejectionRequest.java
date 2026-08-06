package com.autoschedule.workchange.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 교대/대타 요청 거절 사유를 전달한다.
 */
public record WorkChangeRejectionRequest(
        @Size(max = 500, message = "거절 사유는 500자 이하로 입력해 주세요.")
        //@NotBlank(message = "거절 사유를 반드시 입력해주세요")
        String reason
) {

        /**
         * 요청 본문이 없는 현재 UI에서 사용할 기본 요청을 생성한다.
         */
        public static WorkChangeRejectionRequest empty() {
                return new WorkChangeRejectionRequest("");
        }

        /**
         * reason 필드가 누락되거나 null인 경우에도 빈 문자열로 정규화한다.
         */
        public WorkChangeRejectionRequest {
                reason = reason == null ? "" : reason;
        }

}
