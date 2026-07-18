package com.autoschedule.schedulecondition.domain;

import com.autoschedule.global.domain.BaseEntity;
import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 주간 스케줄에 포함되는 요일별 조건을 저장한다.
 */
@Getter
@Entity
@Table(name = "day")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Day extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "day_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "week_schedule_id", nullable = false)
    private WeekSchedule weekSchedule;

    @Enumerated(EnumType.STRING)
    @Column(name = "day_name", nullable = false, length = 20)
    private ScheduleDayName dayName;

    @Column(name = "date", nullable = false)
    private LocalDate date;

    @Column(name = "grouping_id")
    private Integer groupingId;

    @Column(name = "work_change_count", nullable = false)
    private Integer workChangeCount;

    @Column(name = "holiday_status", nullable = false, columnDefinition = "TINYINT(1)")
    private boolean holidayStatus;

    @Column(name = "select_limit_status", nullable = false, columnDefinition = "TINYINT(1)")
    private boolean selectLimitStatus;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DayStatus status;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    /**
     * 요일별 스케줄 조건을 생성한다.
     */
    public static Day create(
            WeekSchedule weekSchedule,
            ScheduleDayName dayName,
            LocalDate date,
            Integer groupingId,
            Integer workChangeCount,
            boolean holidayStatus,
            boolean selectLimitStatus
    ) {
        Day day = new Day();
        day.weekSchedule = weekSchedule;
        day.dayName = dayName;
        day.date = date;
        day.groupingId = groupingId;
        day.workChangeCount = workChangeCount;
        day.holidayStatus = holidayStatus;
        day.selectLimitStatus = selectLimitStatus;
        day.status = DayStatus.ACTIVE;
        return day;
    }

    /**
     * 요일별 스케줄 조건을 삭제 상태로 변경한다.
     */
    public void markDeleted(LocalDateTime deletedAt) {
        this.status = DayStatus.DELETED;
        this.deletedAt = deletedAt;
    }
}