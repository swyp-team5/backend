package com.autoschedule.workerselect.domain;

import com.autoschedule.global.domain.BaseEntity;
import com.autoschedule.schedulecondition.domain.TimeDetail;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 근무자가 제출한 불가능 근무 타임 정보를 저장한다.
 */
@Getter
@Entity
@Table(name = "worker_unavailable")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WorkerUnavailable extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "worker_unavailable_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "time_detail_id")
    private TimeDetail timeDetail;

    @Column(name = "work_place_id", nullable = false)
    private Long workPlaceId;

    @Column(name = "week_schedule_id", nullable = false)
    private Long weekScheduleId;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private WorkerUnavailableStatus status;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    /**
     * 근무자가 제출한 불가능 근무 타임 정보를 생성한다.
     */
    public static WorkerUnavailable create(
            TimeDetail timeDetail,
            Long memberId,
            Long workPlaceId,
            Long weekScheduleId
    ) {
        WorkerUnavailable workerUnavailable = new WorkerUnavailable();
        workerUnavailable.timeDetail = timeDetail;
        workerUnavailable.memberId = memberId;
        workerUnavailable.workPlaceId = workPlaceId;
        workerUnavailable.weekScheduleId = weekScheduleId;
        workerUnavailable.status = WorkerUnavailableStatus.ACTIVE;
        return workerUnavailable;
    }

    /**
     * 근무자별 제출 불가능 정보를 삭제 상태로 변경한다.
     */
    public void markDeleted(LocalDateTime deletedAt) {
        this.status = WorkerUnavailableStatus.DELETED;
        this.deletedAt = deletedAt;
    }

}

