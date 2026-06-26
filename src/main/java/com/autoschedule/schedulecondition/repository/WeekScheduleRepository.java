package com.autoschedule.schedulecondition.repository;

import com.autoschedule.schedulecondition.domain.WeekSchedule;
import com.autoschedule.schedulecondition.domain.WeekScheduleStatus;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 주간 스케줄 조건 저장과 조회를 담당한다.
 */
public interface WeekScheduleRepository extends JpaRepository<WeekSchedule, Long> {

    /**
     * 특정 사업장의 가장 최근 활성 스케줄 조건을 조회한다.
     */
    Optional<WeekSchedule> findFirstByWorkPlace_IdAndStatusAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(
            Long workPlaceId,
            WeekScheduleStatus status
    );

    /**
     * 오늘 일자를 기준으로 다음주 스케줄 조건이 존재하는지 조회한다.
     */
    boolean existsByWorkPlace_IdAndWeekScheduleNameAndStatusAndDeletedAtIsNull(
            Long workPlaceId,
            String weekScheduleName,
            WeekScheduleStatus status
    );

    /**
     * 특정 사업장에 속한 주간 스케줄이 활성 상태로 존재하는지 조회한다.
     */
    Optional<WeekSchedule> findByIdAndWorkPlaceIdAndStatusAndDeletedAtIsNull(
            Long weekScheduleId,
            Long workPlaceId,
            WeekScheduleStatus status
    );
}