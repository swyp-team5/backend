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
 * 자동 스케줄 생성 결과 후보 전체를 JSON 스냅샷으로 저장한다.
 */
@Getter
@Entity
@Table(name = "schedule_preview")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SchedulePreview extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "schedule_preview_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "schedule_generation_run_id", nullable = false)
    private ScheduleGenerationRun scheduleGenerationRun;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "week_schedule_id", nullable = false)
    private WeekSchedule weekSchedule;

    @Column(name = "preview_data", nullable = false, columnDefinition = "json")
    private String previewData;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SchedulePreviewStatus status;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    /**
     * 자동 생성 결과 JSON 스냅샷을 생성한다.
     */
    public static SchedulePreview create(
            ScheduleGenerationRun scheduleGenerationRun,
            WeekSchedule weekSchedule,
            String previewData
    ) {
        SchedulePreview preview = new SchedulePreview();
        preview.scheduleGenerationRun = scheduleGenerationRun;
        preview.weekSchedule = weekSchedule;
        preview.previewData = previewData;
        preview.status = SchedulePreviewStatus.ACTIVE;
        return preview;
    }

    /**
     * 선택된 미리보기 스냅샷을 확정 상태로 변경한다.
     */
    public void markConfirmed() {
        this.status = SchedulePreviewStatus.CONFIRMED;
    }

    /**
     * 미리보기 스냅샷을 삭제 상태로 변경한다.
     */
    public void markDeleted(LocalDateTime deletedAt) {
        this.status = SchedulePreviewStatus.DELETED;
        this.deletedAt = deletedAt;
    }
}
