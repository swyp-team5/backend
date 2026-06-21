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
}