package com.autoschedule.schedulecondition.repository;

import com.autoschedule.schedulecondition.domain.TimeDetail;
import com.autoschedule.schedulecondition.domain.TimeDetailStatus;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
     * 여러 요일에 속한 활성 time_detail 목록을 날짜와 파트 번호 순서로 조회한다.
     */
    List<TimeDetail> findByDay_IdInAndStatusAndDeletedAtIsNullOrderByDay_DateAscWorkPartNoAsc(
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

    /**
     * 특정 사업장/주간 스케줄에 속한 활성 time_detail을 조회한다.
     */
    Optional<TimeDetail> findByIdAndDay_WeekSchedule_IdAndDay_WeekSchedule_WorkPlace_IdAndStatusAndDeletedAtIsNull(
            Long id,
            Long weekScheduleId,
            Long workPlaceId,
            TimeDetailStatus status
    );

    /**
     * 특정 일자 조건에 동일한 근무 파트 번호의 활성 time_detail이 존재하는지 확인한다.
     */
    boolean existsByDay_IdAndWorkPartNoAndStatusAndDeletedAtIsNull(
            Long dayId,
            Integer workPartNo,
            TimeDetailStatus status
    );

    boolean existsByDay_IdAndWorkPartNoAndStatusAndDeletedAtIsNullAndIdNot(
        Long dayId,
        Integer workPartNo,
        TimeDetailStatus status,
        Long id
    );

    /**
     * 삭제 여부와 무관하게 특정 날짜의 마지막 근무 파트 번호를 조회한다.
     * soft delete row도 DB unique(day_id, work_part_no)에 남아 있으므로 반드시 포함해야 한다.
     */
    @Query("""
            select coalesce(max(timeDetail.workPartNo), 0)
            from TimeDetail timeDetail
            where timeDetail.day.id = :dayId
            """)
    int findMaxWorkPartNoByDayId(@Param("dayId") Long dayId);

}
