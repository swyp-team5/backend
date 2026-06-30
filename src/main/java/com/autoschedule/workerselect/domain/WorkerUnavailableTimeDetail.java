package com.autoschedule.workerselect.domain;

import com.autoschedule.global.domain.BaseEntity;
import com.autoschedule.schedulecondition.domain.TimeDetail;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 근무자가 제출한 근무 불가능한 time_detail 목록을 저장한다.
 * 제출 현황(WorkerSelectSubmission)에 종속되며, 빈 리스트 제출 시 레코드가 생성되지 않는다.
 */
@Getter
@Entity
@Table(name = "worker_unavailable_time_detail")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WorkerUnavailableTimeDetail extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "worker_unavailable_time_detail_id")
    private Long id;

    // 제출 현황과 FK 연관관계
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "worker_select_submission_id", nullable = false)
    private WorkerSelectSubmission submission;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "time_detail_id", nullable = false)
    private TimeDetail timeDetail;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private WorkerUnavailableTimeDetailStatus status;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    public static WorkerUnavailableTimeDetail create(WorkerSelectSubmission submission, TimeDetail timeDetail) {
        WorkerUnavailableTimeDetail detail = new WorkerUnavailableTimeDetail();
        detail.submission = submission;
        detail.timeDetail = timeDetail;
        detail.status = WorkerUnavailableTimeDetailStatus.ACTIVE;
        return detail;
    }
}