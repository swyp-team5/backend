package com.autoschedule.member.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * S3 업로드 완료 후 프로필 이미지로 확정할 객체 정보를 표현한다.
 */
public record ProfileImageConfirmRequest(
        @NotBlank(message = "S3 객체 key는 필수입니다.")
        @Size(max = 500, message = "S3 객체 key는 최대 500자까지 입력할 수 있습니다.")
        @Pattern(regexp = "^[^\\\\\\x00-\\x1F\\x7F]+$", message = "S3 객체 key에는 역슬래시나 제어 문자를 포함할 수 없습니다.")
        String objectKey
) {
}
