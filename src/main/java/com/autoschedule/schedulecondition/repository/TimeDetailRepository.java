package com.autoschedule.schedulecondition.repository;

import com.autoschedule.schedulecondition.domain.TimeDetail;
import com.autoschedule.schedulecondition.domain.TimeDetailStatus;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 타임별 상세 정보 저장과 조회를 담당한다.
 */
public interface TimeDetailRepository extends JpaRepository<TimeDetail, Long> {

    /**
     * 특정 요일 조건의 활성 타임 상세 정보를 근무 파트 번호순으로 1건씩 조회한다.
     */
    List<TimeDetail> findByDay_IdAndStatusAndDeletedAtIsNullOrderByWorkPartNoAsc(
            Long dayId,
            TimeDetailStatus status
    );

    /**
     * 특정 요일 조건의 활성 타임 상세 정보를 근무 파트 번호순으로 한번에 조회한다.
     */
    List<TimeDetail> findByDay_IdInAndStatusAndDeletedAtIsNullOrderByWorkPartNoAsc(
            Collection<Long> dayIds,
            TimeDetailStatus status
    );

    /**
     * 특정 사업장에 포함된 활성화된 타임 상세 정보를 조회한다.
     */
    List<TimeDetail> findAllByIdInAndDay_WeekSchedule_WorkPlace_IdAndStatusAndDeletedAtIsNull(
            List<Long> ids,
            Long workPlaceId,
            TimeDetailStatus status
    );

    /**
     * 특정 사업장의 특정 주간 스케줄에 속하는 timeDetail 목록을 조회한다.
     */
    List<TimeDetail> findAllByIdInAndDay_WeekSchedule_IdAndDay_WeekSchedule_WorkPlace_IdAndStatusAndDeletedAtIsNull(
            List<Long> ids,
            Long weekScheduleId,
            Long workPlaceId,
            TimeDetailStatus status
    );

}