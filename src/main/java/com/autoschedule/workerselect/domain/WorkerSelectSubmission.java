package com.autoschedule.workerselect.domain;

import com.autoschedule.global.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 근무자의 근무 불가 스케줄 제출 현황을 저장한다.
 * 근무자가 제출 행위 자체를 기록하며, 실제 불가 타임 목록은 WorkerUnavailableTimeDetail에서 관리한다.
 */
@Getter
@Entity
@Table(name = "worker_select_submission")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WorkerSelectSubmission extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "worker_select_submission_id")
    private Long id;

    @Column(name = "work_place_id", nullable = false)
    private Long workPlaceId;

    @Column(name = "week_schedule_id", nullable = false)
    private Long weekScheduleId;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private WorkerSelectSubmissionStatus status;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    public static WorkerSelectSubmission create(Long workPlaceId, Long weekScheduleId, Long memberId) {
        WorkerSelectSubmission submission = new WorkerSelectSubmission();
        submission.workPlaceId = workPlaceId;
        submission.weekScheduleId = weekScheduleId;
        submission.memberId = memberId;
        submission.status = WorkerSelectSubmissionStatus.ACTIVE;
        return submission;
    }
}