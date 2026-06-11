package com.autoschedule.auth.dto;

import com.autoschedule.workplace.domain.WorkPlaceSize;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 사장님 회원가입에서 함께 생성할 최초 사업장 정보를 받는다.
 */
public record WorkPlaceSignupRequest(
        @NotNull WorkPlaceSize size,
        @NotBlank String name,
        @NotBlank String roadAddress,
        String detailAddress
) {
}
