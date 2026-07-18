package com.autoschedule.notice.dto;

import com.autoschedule.notice.domain.Notice;
import com.autoschedule.notice.domain.NoticeReactionType;
import com.autoschedule.notice.domain.NoticeStatus;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 공지 게시글 상세 응답을 표현한다.
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
        List<NoticeImageResponse> images,
        NoticeReactionType myReactionType,
        List<NoticeReactionCountResponse> reactions,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    /**
     * 공지 엔티티와 작성자 이름을 모바일 응답으로 변환한다.
     */
    public static NoticeResponse from(Notice notice, String writerMemberName) {
        return from(notice, writerMemberName, List.of(), null, List.of());
    }

    /**
     * 공지 엔티티와 작성자 이름, 이미지 목록, 공감 요약을 모바일 응답으로 변환한다.
     */
    public static NoticeResponse from(
            Notice notice,
            String writerMemberName,
            List<NoticeImageResponse> images,
            NoticeReactionType myReactionType,
            List<NoticeReactionCountResponse> reactions
    ) {
        return new NoticeResponse(
                notice.getId(),
                notice.getWorkPlace().getId(),
                notice.getWriterMemberId(),
                writerMemberName,
                notice.getTitle(),
                notice.getContent(),
                notice.isRepresentative(),
                notice.getStatus(),
                images == null ? List.of() : images,
                myReactionType,
                reactions == null ? List.of() : reactions,
                notice.getCreatedAt(),
                notice.getUpdatedAt()
        );
    }
}
