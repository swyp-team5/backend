package com.autoschedule.notice.repository;

import com.autoschedule.notice.domain.Notice;
import com.autoschedule.notice.domain.NoticeStatus;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 공지 게시글 저장과 조회를 담당한다.
 */
public interface NoticeRepository extends JpaRepository<Notice, Long> {

    /**
     * 사업장의 활성 공지 목록을 페이지 단위로 조회한다.
     */
    Page<Notice> findByWorkPlace_IdAndStatusAndDeletedAtIsNull(
            Long workPlaceId,
            NoticeStatus status,
            Pageable pageable
    );

    /**
     * 활성 공지 ID로 공지를 조회한다.
     */
    Optional<Notice> findByIdAndStatusAndDeletedAtIsNull(Long id, NoticeStatus status);

    /**
     * 사업장의 활성 대표 공지를 조회한다.
     */
    Optional<Notice> findFirstByWorkPlace_IdAndRepresentativeTrueAndStatusAndDeletedAtIsNull(
            Long workPlaceId,
            NoticeStatus status
    );

    /**
     * 사업장의 활성 공지 중 가장 최근 작성된 공지를 조회한다.
     */
    Optional<Notice> findFirstByWorkPlace_IdAndStatusAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(
            Long workPlaceId,
            NoticeStatus status
    );

    /**
     * 새 대표 공지를 제외한 같은 사업장의 기존 대표 공지를 해제한다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Notice notice
               set notice.representative = false
             where notice.workPlace.id = :workPlaceId
               and notice.id <> :excludedNoticeId
               and notice.representative = true
               and notice.status = :status
               and notice.deletedAt is null
            """)
    void unsetOtherRepresentatives(
            @Param("workPlaceId") Long workPlaceId,
            @Param("excludedNoticeId") Long excludedNoticeId,
            @Param("status") NoticeStatus status
    );
}
