package com.autoschedule.workerselect.domain;

import com.autoschedule.global.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 근무 불가 제출 반려 이력을 감사 로그로 남긴다.
 * 반려 시 제출 건(WorkerSelectSubmission)이 물리 삭제되므로, submissionId는 FK 없이 참조용으로만 보관한다.
 */
@Getter
@Entity
@Table(name = "worker_select_submission_rejection")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WorkerSelectSubmissionRejection extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "worker_select_submission_rejection_id")
    private Long id;

    @Column(name = "work_place_id", nullable = false)
    private Long workPlaceId;

    @Column(name = "week_schedule_id", nullable = false)
    private Long weekScheduleId;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "submission_id", nullable = false)
    private Long submissionId;

    @Column(name = "rejected_by_member_id", nullable = false)
    private Long rejectedByMemberId;

    public static WorkerSelectSubmissionRejection create(
            Long workPlaceId,
            Long weekScheduleId,
            Long memberId,
            Long submissionId,
            Long rejectedByMemberId
    ) {
        WorkerSelectSubmissionRejection rejection = new WorkerSelectSubmissionRejection();
        rejection.workPlaceId = workPlaceId;
        rejection.weekScheduleId = weekScheduleId;
        rejection.memberId = memberId;
        rejection.submissionId = submissionId;
        rejection.rejectedByMemberId = rejectedByMemberId;
        return rejection;
    }
}
