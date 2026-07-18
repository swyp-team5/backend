package com.autoschedule.notice.dto;

/**
 * 홈 화면 대표 공지 조회 응답을 표현한다.
 */
public record HomeRepresentativeNoticeResponse(
        HomeNoticeSummaryResponse notice
) {
}
