package com.autoschedule.notice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 공지 작성 요청 값을 검증한다.
 */
public record NoticeCreateRequest(
        @NotBlank(message = "공지 제목은 필수입니다.")
        @Size(max = 100, message = "공지 제목은 100자 이하여야 합니다.")
        String title,

        @NotBlank(message = "공지 내용은 필수입니다.")
        @Size(max = 5000, message = "공지 내용은 5000자 이하여야 합니다.")
        String content,

        @NotNull(message = "대표 공지 여부는 필수입니다.")
        Boolean representative
) {
}
