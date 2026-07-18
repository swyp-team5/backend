package com.autoschedule.notice.repository;

import com.autoschedule.notice.domain.NoticeReactionType;

/**
 * 공지별 공감 타입 집계 결과를 표현한다.
 */
public interface NoticeReactionCountProjection {

    Long getNoticeId();

    NoticeReactionType getReactionType();

    long getCount();
}
