package com.autoschedule.notice.domain;

import com.autoschedule.global.domain.BaseEntity;
import com.autoschedule.workplace.domain.WorkPlace;
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
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 사업장별 공지 게시글을 저장한다.
 */
@Getter
@Entity
@Table(name = "notice")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notice extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "notice_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "work_place_id", nullable = false)
    private WorkPlace workPlace;

    @Column(name = "writer_member_id", nullable = false)
    private Long writerMemberId;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(nullable = false, columnDefinition = "TINYINT(1)")
    private boolean representative;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NoticeStatus status;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    /**
     * 사장님이 사업장 공지를 새로 작성한다.
     */
    public static Notice create(
            WorkPlace workPlace,
            Long writerMemberId,
            String title,
            String content,
            boolean representative
    ) {
        Notice notice = new Notice();
        notice.workPlace = workPlace;
        notice.writerMemberId = writerMemberId;
        notice.title = title;
        notice.content = content;
        notice.representative = representative;
        notice.status = NoticeStatus.ACTIVE;
        return notice;
    }

    /**
     * 공지 제목, 내용, 대표 공지 여부를 수정한다.
     */
    public void update(String title, String content, boolean representative) {
        this.title = title;
        this.content = content;
        this.representative = representative;
    }

    /**
     * 전달받은 회원이 공지 작성자인지 확인한다.
     */
    public boolean isWrittenBy(Long memberId) {
        return Objects.equals(this.writerMemberId, memberId);
    }

    /**
     * 다른 공지가 대표 공지로 지정되었을 때 현재 공지를 일반 공지로 변경한다.
     */
    public void unsetRepresentative() {
        this.representative = false;
    }

    /**
     * 공지를 삭제 상태로 변경한다.
     */
    public void markDeleted(LocalDateTime deletedAt) {
        this.status = NoticeStatus.DELETED;
        this.representative = false;
        this.deletedAt = deletedAt;
    }
}
