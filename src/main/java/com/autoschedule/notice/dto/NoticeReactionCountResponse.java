package com.autoschedule.notice.dto;

import com.autoschedule.notice.domain.NoticeReactionType;

/**
 * 공감 타입별 집계 응답을 표현한다.
 */
public record NoticeReactionCountResponse(
        NoticeReactionType reactionType,
        long count
) {
}
