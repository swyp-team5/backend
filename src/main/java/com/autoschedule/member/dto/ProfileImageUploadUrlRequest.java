package com.autoschedule.member.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * 프로필 이미지 S3 업로드 URL 발급 요청 값을 표현한다.
 */
public record ProfileImageUploadUrlRequest(
        @NotBlank(message = "원본 파일명은 필수입니다.")
        @Size(max = 255, message = "원본 파일명은 최대 255자까지 입력할 수 있습니다.")
        @Pattern(regexp = "^[^\\\\/\\p{Cntrl}]+$", message = "원본 파일명에는 경로 문자나 제어 문자를 포함할 수 없습니다.")
        String originalFileName,

        @NotBlank(message = "이미지 content type은 필수입니다.")
        @Size(max = 50, message = "이미지 content type은 최대 50자까지 입력할 수 있습니다.")
        String contentType,

        @NotNull(message = "이미지 파일 크기는 필수입니다.")
        @Positive(message = "이미지 파일 크기는 1 byte 이상이어야 합니다.")
        @Max(value = 10_485_760, message = "프로필 이미지 파일 크기는 최대 10MB까지 허용합니다.")
        Long fileSize
) {
}
