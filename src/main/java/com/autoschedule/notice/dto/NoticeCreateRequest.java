package com.autoschedule.notice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

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
        Boolean representative,

        @Size(max = 5, message = "공지 이미지는 최대 5장까지 첨부할 수 있습니다.")
        List<
                @NotBlank(message = "공지 이미지 object key는 비어 있을 수 없습니다.")
                @Size(max = 500, message = "공지 이미지 object key는 최대 500자까지 입력할 수 있습니다.")
                @Pattern(regexp = "^[^\\\\\\x00-\\x1F\\x7F]+$", message = "공지 이미지 object key에는 역슬래시나 제어 문자를 포함할 수 없습니다.")
                String
                > imageObjectKeys
) {
}
