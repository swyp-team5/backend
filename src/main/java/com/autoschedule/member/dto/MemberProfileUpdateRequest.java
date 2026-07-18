package com.autoschedule.member.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 회원 본인의 프로필 기본 정보 수정 요청 값을 표현한다.
 */
public record MemberProfileUpdateRequest(
        @NotBlank(message = "이름은 필수입니다.")
        @Size(max = 10, message = "이름은 최대 10자까지 입력할 수 있습니다.")
        String name,

        @NotBlank(message = "휴대폰 번호는 필수입니다.")
        @Pattern(regexp = "\\d{11}", message = "휴대폰 번호는 하이픈 없이 11자리 숫자로 입력해주세요.")
        String phoneNumber
) {
}
