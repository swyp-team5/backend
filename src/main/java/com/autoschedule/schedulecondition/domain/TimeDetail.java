package com.autoschedule.schedulecondition.domain;

import com.autoschedule.global.domain.BaseEntity;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.time.LocalTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 요일별 조건에 포함되는 타임별 상세 정보를 저장한다.
 */
@Getter
@Entity
@Table(name = "time_detail")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TimeDetail extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "time_detail_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "day_id", nullable = false)
    private Day day;

    @Column(name = "work_part_no", nullable = false)
    private Long workPartNo;

    @Column(name = "time_name", length = 20)
    private String timeName;

    @Column(name = "worker_count", nullable = false)
    private Long workerCount;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "close_time", nullable = false)
    private LocalTime closeTime;

    @Column(name = "rest_time", nullable = false)
    private Integer restTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TimeDetailStatus status;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    /**
     * 타임별 상세 정보를 생성한다.
     */
    public static TimeDetail create(
            Day day,
            Long workPartNo,
            String timeName,
            Long workerCount,
            LocalTime startTime,
            LocalTime closeTime,
            Integer restTime
    ) {
        TimeDetail timeDetail = new TimeDetail();
        timeDetail.day = day;
        timeDetail.workPartNo = workPartNo;
        timeDetail.timeName = timeName;
        timeDetail.workerCount = workerCount;
        timeDetail.startTime = startTime;
        timeDetail.closeTime = closeTime;
        timeDetail.restTime = restTime;
        timeDetail.status = TimeDetailStatus.ACTIVE;
        return timeDetail;
    }

    /**
     * 타임별 상세 정보를 삭제 상태로 변경한다.
     */
    public void markDeleted(LocalDateTime deletedAt) {
        this.status = TimeDetailStatus.DELETED;
        this.deletedAt = deletedAt;
    }
}