package com.autoschedule.notice.repository;

import com.autoschedule.notice.domain.NoticeComment;
import com.autoschedule.notice.domain.NoticeCommentStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 공지 댓글 저장과 커서 조회를 담당한다.
 */
public interface NoticeCommentRepository extends JpaRepository<NoticeComment, Long> {

    /**
     * 특정 공지의 활성 댓글을 댓글 ID 기준 오름차순으로 조회한다.
     */
    List<NoticeComment> findByNotice_IdAndStatusAndDeletedAtIsNullAndIdGreaterThanOrderByIdAsc(
            Long noticeId,
            NoticeCommentStatus status,
            Long cursorId,
            Pageable pageable
    );

    /**
     * 특정 공지에 속한 활성 댓글을 조회한다.
     */
    Optional<NoticeComment> findByIdAndNotice_IdAndStatusAndDeletedAtIsNull(
            Long id,
            Long noticeId,
            NoticeCommentStatus status
    );
}
