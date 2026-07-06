package com.autoschedule.schedule.repository;

import com.autoschedule.schedule.domain.SchedulePreview;
import com.autoschedule.schedule.domain.SchedulePreviewStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 자동 생성 미리보기 JSON 스냅샷을 저장하고 조회한다.
 */
public interface SchedulePreviewRepository extends JpaRepository<SchedulePreview, Long> {

    /**
     * 특정 자동 생성 실행에 속한 미리보기 스냅샷을 조회한다.
     */
    Optional<SchedulePreview> findByIdAndScheduleGenerationRun_IdAndWeekSchedule_IdAndStatusAndDeletedAtIsNull(
            Long id,
            Long scheduleGenerationRunId,
            Long weekScheduleId,
            SchedulePreviewStatus status
    );

    /**
     * 특정 자동 생성 실행의 사용 가능한 미리보기 스냅샷을 조회한다.
     */
    Optional<SchedulePreview> findFirstByScheduleGenerationRun_IdAndWeekSchedule_IdAndStatusAndDeletedAtIsNullOrderByIdDesc(
            Long scheduleGenerationRunId,
            Long weekScheduleId,
            SchedulePreviewStatus status
    );

    /**
     * 여러 자동 생성 이력에 속한 활성 미리보기 스냅샷을 조회한다.
     */
    List<SchedulePreview> findByScheduleGenerationRun_IdInAndStatusAndDeletedAtIsNull(
            Collection<Long> scheduleGenerationRunIds,
            SchedulePreviewStatus status
    );
}
