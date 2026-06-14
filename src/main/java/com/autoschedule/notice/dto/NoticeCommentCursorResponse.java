package com.autoschedule.notice.dto;

import java.util.List;

/**
 * 공지 댓글 커서 조회 응답을 표현한다.
 */
public record NoticeCommentCursorResponse(
        List<NoticeCommentResponse> content,
        Long nextCursorId,
        boolean hasNext
) {
}
