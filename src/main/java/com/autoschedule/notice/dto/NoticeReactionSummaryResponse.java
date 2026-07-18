package com.autoschedule.notice.dto;

import com.autoschedule.notice.domain.NoticeReactionType;
import java.util.List;

/**
 * 공지 1건의 공감 집계와 현재 회원의 공감을 표현한다.
 */
public record NoticeReactionSummaryResponse(
        Long noticeId,
        NoticeReactionType myReactionType,
        List<NoticeReactionCountResponse> reactions
) {
}
