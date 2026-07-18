package com.autoschedule.schedule.domain;

import com.autoschedule.global.domain.BaseEntity;
import com.autoschedule.schedulecondition.domain.Day;
import com.autoschedule.schedulecondition.domain.TimeDetail;
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
 * 확정된 주간 스케줄 안에서 근무자가 특정 time_detail에 배정된 사실을 저장한다.
 */
@Getter
@Entity
@Table(name = "confirmed_schedule_assignment")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ConfirmedScheduleAssignment extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "confirmed_schedule_assignment_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "confirmed_week_schedule_id", nullable = false)
    private ConfirmedWeekSchedule confirmedWeekSchedule;

    @Column(name = "work_place_id", nullable = false)
    private Long workPlaceId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "week_schedule_id", nullable = false)
    private WeekSchedule weekSchedule;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "day_id", nullable = false)
    private Day day;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "time_detail_id", nullable = false)
    private TimeDetail timeDetail;

    @Column(name = "worker_member_id", nullable = false)
    private Long workerMemberId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ConfirmedScheduleAssignmentStatus status;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    /**
     * 확정 스케줄 안에 근무자 배정 row를 생성한다.
     */
    public static ConfirmedScheduleAssignment create(
            ConfirmedWeekSchedule confirmedWeekSchedule,
            Long workPlaceId,
            WeekSchedule weekSchedule,
            Day day,
            TimeDetail timeDetail,
            Long workerMemberId
    ) {
        ConfirmedScheduleAssignment assignment = new ConfirmedScheduleAssignment();
        assignment.confirmedWeekSchedule = confirmedWeekSchedule;
        assignment.workPlaceId = workPlaceId;
        assignment.weekSchedule = weekSchedule;
        assignment.day = day;
        assignment.timeDetail = timeDetail;
        assignment.workerMemberId = workerMemberId;
        assignment.status = ConfirmedScheduleAssignmentStatus.ACTIVE;
        return assignment;
    }

    /**
     * 확정 배정을 삭제 상태로 변경한다.
     */
    public void markDeleted(LocalDateTime deletedAt) {
        this.status = ConfirmedScheduleAssignmentStatus.DELETED;
        this.deletedAt = deletedAt;
    }
}
