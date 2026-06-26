package com.autoschedule.schedulecondition.repository;

import com.autoschedule.schedulecondition.domain.Day;
import com.autoschedule.schedulecondition.domain.DayStatus;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 요일별 스케줄 조건 저장과 조회를 담당한다.
 */
public interface DayRepository extends JpaRepository<Day, Long> {

    /**
     * 특정 주간 스케줄에 속한 활성 요일 조건을 조회한다.
     */
    List<Day> findByWeekSchedule_IdAndStatusAndDeletedAtIsNullOrderByDateAscIdAsc(
            Long weekScheduleId,
            DayStatus status
    );

    /**
     * 특정 주간 스케줄의 특정 일자 조건을 조회한다.
     */
    Optional<Day> findByWeekSchedule_IdAndDateAndStatusAndDeletedAtIsNull(
            Long weekScheduleId,
            LocalDate date,
            DayStatus status
    );

}