package com.autoschedule.notice.dto;

import com.autoschedule.notice.domain.Notice;
import com.autoschedule.notice.domain.NoticeStatus;
import java.time.LocalDateTime;

/**
 * 공지 게시글 단건 응답을 표현한다.
 */
public record NoticeResponse(
        Long noticeId,
        Long workPlaceId,
        Long writerMemberId,
        String writerMemberName,
        String title,
        String content,
        boolean representative,
        NoticeStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    /**
     * 공지 엔티티와 작성자 이름을 모바일 응답으로 변환한다.
     */
    public static NoticeResponse from(Notice notice, String writerMemberName) {
        return new NoticeResponse(
                notice.getId(),
                notice.getWorkPlace().getId(),
                notice.getWriterMemberId(),
                writerMemberName,
                notice.getTitle(),
                notice.getContent(),
                notice.isRepresentative(),
                notice.getStatus(),
                notice.getCreatedAt(),
                notice.getUpdatedAt()
        );
    }
}
