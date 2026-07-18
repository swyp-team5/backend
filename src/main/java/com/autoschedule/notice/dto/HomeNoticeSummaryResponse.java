package com.autoschedule.notice.dto;

import com.autoschedule.notice.domain.Notice;
import java.time.LocalDateTime;

/**
 * 홈 화면에 노출할 대표 공지 요약 정보를 표현한다.
 */
public record HomeNoticeSummaryResponse(
        Long noticeId,
        String title,
        String content,
        String writerMemberName,
        LocalDateTime createdAt
) {

    /**
     * 공지 엔티티와 작성자 이름을 홈 대표 공지 응답으로 변환한다.
     */
    public static HomeNoticeSummaryResponse from(Notice notice, String writerMemberName) {
        return new HomeNoticeSummaryResponse(
                notice.getId(),
                notice.getTitle(),
                notice.getContent(),
                writerMemberName,
                notice.getCreatedAt()
        );
    }
}
