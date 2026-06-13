package com.autoschedule.crew.domain;

import com.autoschedule.global.domain.BaseEntity;
import com.autoschedule.member.domain.Member;
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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 사장님이 근무자를 사업장 크루로 초대하기 위해 생성한 1회용 코드를 저장한다.
 */
@Getter
@Entity
@Table(name = "crew_invitation")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CrewInvitation extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "crew_invitation_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "work_place_id", nullable = false)
    private WorkPlace workPlace;

    @Column(name = "created_by_member_id", nullable = false)
    private Long createdByMemberId;

    @Column(name = "invite_code", nullable = false, length = 6, columnDefinition = "char(6)")
    private String inviteCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CrewInvitationStatus status;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "used_at")
    private LocalDateTime usedAt;

    @Column(name = "used_by_member_id")
    private Long usedByMemberId;

    @Column(name = "failed_attempt_count", nullable = false)
    private int failedAttemptCount;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    /**
     * 사용 가능한 새 초대 코드를 생성한다.
     */
    public static CrewInvitation create(
            WorkPlace workPlace,
            Long createdByMemberId,
            String inviteCode,
            LocalDateTime expiresAt
    ) {
        CrewInvitation invitation = new CrewInvitation();
        invitation.workPlace = workPlace;
        invitation.createdByMemberId = createdByMemberId;
        invitation.inviteCode = inviteCode;
        invitation.status = CrewInvitationStatus.ACTIVE;
        invitation.expiresAt = expiresAt;
        invitation.failedAttemptCount = 0;
        return invitation;
    }

    /**
     * 현재 시각 기준으로 초대 코드가 만료되었는지 확인한다.
     */
    public boolean isExpired(LocalDateTime now) {
        return !expiresAt.isAfter(now);
    }

    /**
     * 초대 코드를 사용 완료 상태로 변경하고 사용자를 기록한다.
     */
    public void markUsed(Member usedBy, LocalDateTime usedAt) {
        this.status = CrewInvitationStatus.USED;
        this.usedByMemberId = usedBy.getId();
        this.usedAt = usedAt;
    }

    /**
     * 초대 코드가 만료되었음을 기록한다.
     */
    public void markExpired() {
        this.status = CrewInvitationStatus.EXPIRED;
    }

    /**
     * 실패 횟수 초과로 초대 코드를 잠금 상태로 변경한다.
     */
    public void markLocked(int failedAttemptCount) {
        this.status = CrewInvitationStatus.LOCKED;
        this.failedAttemptCount = failedAttemptCount;
    }

    /**
     * Redis에서 집계한 실패 횟수를 RDB 감사 값으로 동기화한다.
     */
    public void updateFailedAttemptCount(int failedAttemptCount) {
        this.failedAttemptCount = failedAttemptCount;
    }
}
