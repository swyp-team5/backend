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
 * 공지 게시글에 사장님이 작성한 댓글을 저장한다.
 */
@Getter
@Entity
@Table(name = "notice_comment")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NoticeComment extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "notice_comment_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "notice_id", nullable = false)
    private Notice notice;

    @Column(name = "writer_member_id", nullable = false)
    private Long writerMemberId;

    @Column(nullable = false, length = 500)
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NoticeCommentStatus status;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    /**
     * 사장님이 공지에 댓글을 작성한다.
     */
    public static NoticeComment create(Notice notice, Long writerMemberId, String content) {
        NoticeComment comment = new NoticeComment();
        comment.notice = notice;
        comment.writerMemberId = writerMemberId;
        comment.content = content;
        comment.status = NoticeCommentStatus.ACTIVE;
        return comment;
    }

    /**
     * 댓글 내용을 수정한다.
     */
    public void update(String content) {
        this.content = content;
    }

    /**
     * 댓글을 삭제 상태로 변경한다.
     */
    public void markDeleted(LocalDateTime deletedAt) {
        this.status = NoticeCommentStatus.DELETED;
        this.deletedAt = deletedAt;
    }
}
