package com.autoschedule.workplace.dto;

import jakarta.validation.constraints.Pattern;

/**
 * 사업장 전화번호 부가 정보를 추가, 수정 또는 삭제할 때 사용하는 요청 값이다.
 */
public record WorkPlacePhoneNumberUpdateRequest(
        @Pattern(regexp = "^\\d{8,11}$", message = "사업장 전화번호는 하이픈 없이 8~11자리 숫자로 입력해주세요.")
        String phoneNumber
) {
}
