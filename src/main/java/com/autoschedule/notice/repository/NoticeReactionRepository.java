package com.autoschedule.notice.repository;

import com.autoschedule.notice.domain.NoticeReaction;
import com.autoschedule.notice.domain.NoticeReactionStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 공지 공감 저장과 집계 조회를 담당한다.
 */
public interface NoticeReactionRepository extends JpaRepository<NoticeReaction, Long> {

    /**
     * 공지와 회원 ID로 기존 공감 이력을 조회한다.
     */
    Optional<NoticeReaction> findByNotice_IdAndMemberId(Long noticeId, Long memberId);

    /**
     * 공지와 회원 ID 목록 기준으로 활성 공감 목록을 조회한다.
     */
    @EntityGraph(attributePaths = "notice")
    List<NoticeReaction> findByNotice_IdInAndMemberIdAndStatus(
            Collection<Long> noticeIds,
            Long memberId,
            NoticeReactionStatus status
    );

    /**
     * 공지 ID 목록 기준으로 활성 공감 수를 타입별로 집계한다.
     */
    @Query("""
            select reaction.notice.id as noticeId,
                   reaction.reactionType as reactionType,
                   count(reaction.id) as count
              from NoticeReaction reaction
             where reaction.notice.id in :noticeIds
               and reaction.status = :status
               and reaction.deletedAt is null
             group by reaction.notice.id, reaction.reactionType
            """)
    List<NoticeReactionCountProjection> countByNoticeIdsAndStatus(
            @Param("noticeIds") Collection<Long> noticeIds,
            @Param("status") NoticeReactionStatus status
    );
}
