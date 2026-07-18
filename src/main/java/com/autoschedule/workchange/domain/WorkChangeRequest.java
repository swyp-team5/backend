package com.autoschedule.workchange.domain;

import com.autoschedule.global.domain.BaseEntity;
import com.autoschedule.schedule.domain.ConfirmedScheduleAssignment;
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
 * 확정된 근무에 대한 교대/대타 요청과 승인 흐름을 저장한다.
 */
@Getter
@Entity
@Table(name = "work_change_request")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WorkChangeRequest extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "work_change_request_id")
    private Long id;

    @Column(name = "work_place_id", nullable = false)
    private Long workPlaceId;

    @Column(name = "requester_member_id", nullable = false)
    private Long requesterMemberId;

    @Column(name = "target_member_id", nullable = false)
    private Long targetMemberId;

    @Enumerated(EnumType.STRING)
    @Column(name = "request_type", nullable = false, length = 30)
    private WorkChangeRequestType requestType;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "request_assignment_id", nullable = false)
    private ConfirmedScheduleAssignment requestAssignment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_assignment_id")
    private ConfirmedScheduleAssignment targetAssignment;

    @Column(nullable = false, length = 500)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private WorkChangeRequestStatus status;

    @Column(name = "target_responded_at")
    private LocalDateTime targetRespondedAt;

    @Column(name = "target_rejection_reason", length = 500)
    private String targetRejectionReason;

    @Column(name = "processed_by_member_id")
    private Long processedByMemberId;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    @Column(name = "owner_rejection_reason", length = 500)
    private String ownerRejectionReason;

    @Column(name = "canceled_at")
    private LocalDateTime canceledAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    /**
     * 요청 근무를 대상 근무자에게 넘기는 대타 요청을 생성한다.
     */
    public static WorkChangeRequest createSubstitute(
            Long workPlaceId,
            Long requesterMemberId,
            Long targetMemberId,
            ConfirmedScheduleAssignment requestAssignment,
            String reason
    ) {
        WorkChangeRequest request = new WorkChangeRequest();
        request.workPlaceId = workPlaceId;
        request.requesterMemberId = requesterMemberId;
        request.targetMemberId = targetMemberId;
        request.requestType = WorkChangeRequestType.SUBSTITUTE;
        request.requestAssignment = requestAssignment;
        request.reason = reason;
        request.status = WorkChangeRequestStatus.REQUESTED;
        return request;
    }

    /**
     * 요청 근무와 대상 근무자의 근무를 서로 바꾸는 교대 요청을 생성한다.
     */
    public static WorkChangeRequest createShiftSwap(
            Long workPlaceId,
            Long requesterMemberId,
            Long targetMemberId,
            ConfirmedScheduleAssignment requestAssignment,
            ConfirmedScheduleAssignment targetAssignment,
            String reason
    ) {
        WorkChangeRequest request = createSubstitute(
                workPlaceId,
                requesterMemberId,
                targetMemberId,
                requestAssignment,
                reason
        );
        request.requestType = WorkChangeRequestType.SHIFT_SWAP;
        request.targetAssignment = targetAssignment;
        return request;
    }

    /**
     * 대상 근무자가 요청을 수락한 상태로 변경한다.
     */
    public void acceptByTarget(LocalDateTime respondedAt) {
        this.status = WorkChangeRequestStatus.ACCEPTED_BY_TARGET;
        this.targetRespondedAt = respondedAt;
    }

    /**
     * 대상 근무자가 요청을 거절한 상태로 변경한다.
     */
    public void rejectByTarget(String rejectionReason, LocalDateTime respondedAt) {
        this.status = WorkChangeRequestStatus.REJECTED_BY_TARGET;
        this.targetRejectionReason = rejectionReason;
        this.targetRespondedAt = respondedAt;
    }

    /**
     * 사장님이 최종 승인한 상태로 변경한다.
     */
    public void approveByOwner(Long ownerMemberId, LocalDateTime processedAt) {
        this.status = WorkChangeRequestStatus.APPROVED;
        this.processedByMemberId = ownerMemberId;
        this.processedAt = processedAt;
    }

    /**
     * 사장님이 최종 거절한 상태로 변경한다.
     */
    public void rejectByOwner(Long ownerMemberId, String rejectionReason, LocalDateTime processedAt) {
        this.status = WorkChangeRequestStatus.REJECTED_BY_OWNER;
        this.processedByMemberId = ownerMemberId;
        this.ownerRejectionReason = rejectionReason;
        this.processedAt = processedAt;
    }

    /**
     * 요청자가 대상 근무자 응답 전 요청을 취소한다.
     */
    public void cancel(LocalDateTime canceledAt) {
        this.status = WorkChangeRequestStatus.CANCELED;
        this.canceledAt = canceledAt;
    }
}
