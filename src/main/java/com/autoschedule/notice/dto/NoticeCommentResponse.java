package com.autoschedule.notice.dto;

import com.autoschedule.notice.domain.NoticeComment;
import com.autoschedule.notice.domain.NoticeCommentStatus;
import java.time.LocalDateTime;

/**
 * 공지 댓글 단건 응답을 표현한다.
 */
public record NoticeCommentResponse(
        Long commentId,
        Long noticeId,
        Long writerMemberId,
        String writerMemberName,
        String content,
        NoticeCommentStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    /**
     * 댓글 엔티티와 작성자 이름을 모바일 응답으로 변환한다.
     */
    public static NoticeCommentResponse from(NoticeComment comment, String writerMemberName) {
        return new NoticeCommentResponse(
                comment.getId(),
                comment.getNotice().getId(),
                comment.getWriterMemberId(),
                writerMemberName,
                comment.getContent(),
                comment.getStatus(),
                comment.getCreatedAt(),
                comment.getUpdatedAt()
        );
    }
}
