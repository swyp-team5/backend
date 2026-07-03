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
 * 사장이 최종 확정한 주간 스케줄의 헤더 정보를 저장한다.
 */
@Getter
@Entity
@Table(name = "confirmed_week_schedule")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ConfirmedWeekSchedule extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "confirmed_week_schedule_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "week_schedule_id", nullable = false)
    private WeekSchedule weekSchedule;

    @Column(name = "work_place_id", nullable = false)
    private Long workPlaceId;

    @Column(name = "schedule_generation_run_id")
    private Long scheduleGenerationRunId;

    @Column(name = "schedule_preview_id")
    private Long schedulePreviewId;

    @Column(name = "selected_candidate_no")
    private Integer selectedCandidateNo;

    @Column(name = "confirmed_by_member_id", nullable = false)
    private Long confirmedByMemberId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ConfirmedWeekScheduleStatus status;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    /**
     * 선택한 자동 생성 후보를 기준으로 확정 주간 스케줄을 생성한다.
     */
    public static ConfirmedWeekSchedule create(
            WeekSchedule weekSchedule,
            Long workPlaceId,
            Long scheduleGenerationRunId,
            Long schedulePreviewId,
            Integer selectedCandidateNo,
            Long confirmedByMemberId
    ) {
        ConfirmedWeekSchedule confirmed = new ConfirmedWeekSchedule();
        confirmed.weekSchedule = weekSchedule;
        confirmed.workPlaceId = workPlaceId;
        confirmed.scheduleGenerationRunId = scheduleGenerationRunId;
        confirmed.schedulePreviewId = schedulePreviewId;
        confirmed.selectedCandidateNo = selectedCandidateNo;
        confirmed.confirmedByMemberId = confirmedByMemberId;
        confirmed.status = ConfirmedWeekScheduleStatus.ACTIVE;
        return confirmed;
    }

    /**
     * 확정 주간 스케줄을 삭제 상태로 변경한다.
     */
    public void markDeleted(LocalDateTime deletedAt) {
        this.status = ConfirmedWeekScheduleStatus.DELETED;
        this.deletedAt = deletedAt;
    }
}
