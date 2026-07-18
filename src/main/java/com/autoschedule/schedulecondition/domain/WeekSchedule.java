package com.autoschedule.schedulecondition.domain;

import com.autoschedule.global.domain.BaseEntity;
import com.autoschedule.workplace.domain.WorkPlace;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 사장이 생성한 주간 스케줄 조건을 저장한다.
 */
@Getter
@Entity
@Table(name = "week_schedule")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WeekSchedule extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "week_schedule_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "work_place_id", nullable = false)
    private WorkPlace workPlace;

    @Column(name = "week_schedule_name", nullable = false, length = 20)
    private String weekScheduleName;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(name = "work_place_open_time", nullable = false)
    private LocalTime workPlaceOpenTime;

    @Column(name = "work_place_close_time", nullable = false)
    private LocalTime workPlaceCloseTime;

    @Column(name = "min_personal_work_count", nullable = false)
    private Integer minPersonalWorkCount;

    @Column(name = "max_personal_work_count", nullable = false)
    private Integer maxPersonalWorkCount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private WeekScheduleStatus status;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    /**
     * 주간 스케줄 조건을 생성한다.
     */
    public static WeekSchedule create(
            WorkPlace workPlace,
            String weekScheduleName,
            LocalDate dueDate,
            LocalTime workPlaceOpenTime,
            LocalTime workPlaceCloseTime,
            Integer minPersonalWorkCount,
            Integer maxPersonalWorkCount
    ) {
        WeekSchedule weekSchedule = new WeekSchedule();
        weekSchedule.workPlace = workPlace;
        weekSchedule.weekScheduleName = weekScheduleName;
        weekSchedule.dueDate = dueDate;
        weekSchedule.workPlaceOpenTime = workPlaceOpenTime;
        weekSchedule.workPlaceCloseTime = workPlaceCloseTime;
        weekSchedule.minPersonalWorkCount = minPersonalWorkCount;
        weekSchedule.maxPersonalWorkCount = maxPersonalWorkCount;
        weekSchedule.status = WeekScheduleStatus.ACTIVE;
        return weekSchedule;
    }

    /**
     * 주간 스케줄 조건을 삭제 상태로 변경한다.
     */
    public void markDeleted(LocalDateTime deletedAt) {
        this.status = WeekScheduleStatus.DELETED;
        this.weekScheduleName = "DELETED-" + id;
        this.deletedAt = deletedAt;
    }
}
