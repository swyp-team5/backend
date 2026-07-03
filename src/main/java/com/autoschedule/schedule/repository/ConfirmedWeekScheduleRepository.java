package com.autoschedule.schedule.repository;

import com.autoschedule.schedule.domain.ConfirmedWeekSchedule;
import com.autoschedule.schedule.domain.ConfirmedWeekScheduleStatus;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 확정 주간 스케줄 헤더를 저장하고 조회한다.
 */
public interface ConfirmedWeekScheduleRepository extends JpaRepository<ConfirmedWeekSchedule, Long> {

    /**
     * 특정 주간 스케줄에 활성 확정 스케줄이 존재하는지 확인한다.
     */
    boolean existsByWeekSchedule_IdAndStatusAndDeletedAtIsNull(
            Long weekScheduleId,
            ConfirmedWeekScheduleStatus status
    );

    /**
     * 특정 사업장과 주간 스케줄에 속한 확정 스케줄을 조회한다.
     */
    Optional<ConfirmedWeekSchedule> findByIdAndWorkPlaceIdAndWeekSchedule_IdAndStatusAndDeletedAtIsNull(
            Long id,
            Long workPlaceId,
            Long weekScheduleId,
            ConfirmedWeekScheduleStatus status
    );

    /**
     * 특정 사업장에 속한 활성 확정 주간 스케줄을 조회한다.
     */
    Optional<ConfirmedWeekSchedule> findByIdAndWorkPlaceIdAndStatusAndDeletedAtIsNull(
            Long id,
            Long workPlaceId,
            ConfirmedWeekScheduleStatus status
    );
}
