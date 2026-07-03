package com.autoschedule.schedule.domain;

import com.autoschedule.global.domain.BaseEntity;
import com.autoschedule.schedulecondition.domain.WeekSchedule;
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
 * 사장이 특정 주간 스케줄 조건으로 자동 스케줄 생성을 실행한 이력을 저장한다.
 */
@Getter
@Entity
@Table(name = "schedule_generation_run")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ScheduleGenerationRun extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "schedule_generation_run_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "week_schedule_id", nullable = false)
    private WeekSchedule weekSchedule;

    @Column(name = "work_place_id", nullable = false)
    private Long workPlaceId;

    @Column(name = "requested_by_member_id", nullable = false)
    private Long requestedByMemberId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ScheduleGenerationRunStatus status;

    @Column(name = "total_preview_count", nullable = false)
    private Integer totalPreviewCount;

    @Column(name = "algorithm_version", length = 30)
    private String algorithmVersion;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    /**
     * 성공한 자동 스케줄 생성 실행 이력을 생성한다.
     */
    public static ScheduleGenerationRun generated(
            WeekSchedule weekSchedule,
            Long workPlaceId,
            Long requestedByMemberId,
            int totalPreviewCount,
            String algorithmVersion
    ) {
        ScheduleGenerationRun run = new ScheduleGenerationRun();
        run.weekSchedule = weekSchedule;
        run.workPlaceId = workPlaceId;
        run.requestedByMemberId = requestedByMemberId;
        run.status = ScheduleGenerationRunStatus.GENERATED;
        run.totalPreviewCount = totalPreviewCount;
        run.algorithmVersion = algorithmVersion;
        return run;
    }

    /**
     * 실패한 자동 스케줄 생성 실행 이력을 생성한다.
     */
    public static ScheduleGenerationRun failed(
            WeekSchedule weekSchedule,
            Long workPlaceId,
            Long requestedByMemberId,
            String algorithmVersion,
            String failureReason
    ) {
        ScheduleGenerationRun run = new ScheduleGenerationRun();
        run.weekSchedule = weekSchedule;
        run.workPlaceId = workPlaceId;
        run.requestedByMemberId = requestedByMemberId;
        run.status = ScheduleGenerationRunStatus.FAILED;
        run.totalPreviewCount = 0;
        run.algorithmVersion = algorithmVersion;
        run.failureReason = failureReason;
        return run;
    }

    /**
     * 기존 자동 생성 이력을 더 이상 사용하지 않는 상태로 변경한다.
     */
    public void markDeleted(LocalDateTime deletedAt) {
        this.status = ScheduleGenerationRunStatus.DELETED;
        this.deletedAt = deletedAt;
    }
}
