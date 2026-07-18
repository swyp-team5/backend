package com.autoschedule.notice.domain;

import com.autoschedule.global.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 근무자가 공지에 남긴 공감 1건을 저장한다.
 */
@Getter
@Entity
@Table(name = "notice_reaction")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NoticeReaction extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "notice_reaction_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "notice_id", nullable = false)
    private Notice notice;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Enumerated(EnumType.STRING)
    @Column(name = "reaction_type", nullable = false, length = 20)
    private NoticeReactionType reactionType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NoticeReactionStatus status;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    /**
     * 신규 공감 row를 생성한다.
     */
    public static NoticeReaction create(Notice notice, Long memberId, NoticeReactionType reactionType) {
        NoticeReaction reaction = new NoticeReaction();
        reaction.notice = notice;
        reaction.memberId = memberId;
        reaction.reactionType = reactionType;
        reaction.status = NoticeReactionStatus.ACTIVE;
        return reaction;
    }

    /**
     * 선택한 공감으로 활성화하거나 변경한다.
     */
    public void activate(NoticeReactionType reactionType) {
        this.reactionType = reactionType;
        this.status = NoticeReactionStatus.ACTIVE;
        this.deletedAt = null;
    }

    /**
     * 현재 공감이 같은 타입인지 확인한다.
     */
    public boolean isSameActiveReaction(NoticeReactionType reactionType) {
        return this.status == NoticeReactionStatus.ACTIVE && this.reactionType == reactionType;
    }

    /**
     * 공감을 취소한다.
     */
    public void markDeleted(LocalDateTime deletedAt) {
        this.status = NoticeReactionStatus.DELETED;
        this.deletedAt = deletedAt;
    }
}
