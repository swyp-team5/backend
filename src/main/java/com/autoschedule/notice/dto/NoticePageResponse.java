package com.autoschedule.notice.dto;

import com.autoschedule.notice.domain.Notice;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;

/**
 * 공지 목록 페이지 응답을 표현한다.
 */
public record NoticePageResponse(
        List<NoticeResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {

    /**
     * 공지 페이지와 작성자 이름 맵을 공지 목록 응답으로 변환한다.
     */
    public static NoticePageResponse from(Page<Notice> notices, Map<Long, String> writerNames) {
        List<NoticeResponse> content = notices.getContent()
                .stream()
                .map(notice -> NoticeResponse.from(notice, writerNames.get(notice.getWriterMemberId())))
                .toList();

        return new NoticePageResponse(
                content,
                notices.getNumber(),
                notices.getSize(),
                notices.getTotalElements(),
                notices.getTotalPages()
        );
    }
}
