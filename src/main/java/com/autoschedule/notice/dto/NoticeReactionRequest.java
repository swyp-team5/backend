package com.autoschedule.notice.dto;

import com.autoschedule.notice.domain.NoticeReactionType;
import jakarta.validation.constraints.NotNull;

/**
 * 공지 공감 선택 요청을 표현한다.
 */
public record NoticeReactionRequest(
        @NotNull(message = "공지 공감 종류는 필수입니다.")
        NoticeReactionType reactionType
) {
}
