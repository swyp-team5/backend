package com.autoschedule.workplace.dto;

import com.autoschedule.workplace.domain.WorkPlaceSize;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 사장님이 추가 사업장을 생성할 때 입력하는 요청 값이다.
 */
public record WorkPlaceCreateRequest(
        @NotNull(message = "사업장 규모는 필수입니다.")
        WorkPlaceSize size,

        @NotBlank(message = "사업장 이름은 필수입니다.")
        @Size(max = 100, message = "사업장 이름은 100자 이하로 입력해주세요.")
        String name,

        @NotBlank(message = "사업장 도로명 주소는 필수입니다.")
        @Size(max = 255, message = "사업장 도로명 주소는 255자 이하로 입력해주세요.")
        String roadAddress,

        @Size(max = 100, message = "사업장 상세 주소는 100자 이하로 입력해주세요.")
        String detailAddress,

        @Pattern(regexp = "^\\d{8,11}$", message = "사업장 전화번호는 하이픈 없이 8~11자리 숫자로 입력해주세요.")
        String phoneNumber
) {
}
