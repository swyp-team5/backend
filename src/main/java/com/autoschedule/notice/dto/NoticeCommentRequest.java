package com.autoschedule.notice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 공지 댓글 작성과 수정 요청 값을 검증한다.
 */
public record NoticeCommentRequest(
        @NotBlank(message = "댓글 내용은 필수입니다.")
        @Size(max = 500, message = "댓글 내용은 500자 이하여야 합니다.")
        String content
) {
}
