package com.autoschedule.schedule.repository;

import com.autoschedule.schedule.domain.ScheduleGenerationRun;
import com.autoschedule.schedule.domain.ScheduleGenerationRunStatus;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 자동 스케줄 생성 실행 이력을 저장하고 조회한다.
 */
public interface ScheduleGenerationRunRepository extends JpaRepository<ScheduleGenerationRun, Long> {

    /**
     * 특정 주간 스케줄에 속한 자동 생성 실행 이력을 상태 기준으로 조회한다.
     */
    Optional<ScheduleGenerationRun> findByIdAndWeekSchedule_IdAndStatusAndDeletedAtIsNull(
            Long id,
            Long weekScheduleId,
            ScheduleGenerationRunStatus status
    );
}
