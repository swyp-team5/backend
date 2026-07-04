package com.autoschedule.schedule.repository;

import com.autoschedule.schedule.domain.ConfirmedScheduleAssignment;
import com.autoschedule.schedule.domain.ConfirmedScheduleAssignmentStatus;
import com.autoschedule.schedule.domain.ConfirmedWeekScheduleStatus;
import com.autoschedule.schedulecondition.domain.DayStatus;
import com.autoschedule.schedulecondition.domain.TimeDetailStatus;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 확정 근무 배정을 저장하고 조회한다.
 */
public interface ConfirmedScheduleAssignmentRepository extends JpaRepository<ConfirmedScheduleAssignment, Long> {

    /**
     * 특정 확정 스케줄에 속한 활성 배정 목록을 조회한다.
     */
    List<ConfirmedScheduleAssignment> findByConfirmedWeekSchedule_IdAndStatusAndDeletedAtIsNullOrderByIdAsc(
            Long confirmedWeekScheduleId,
            ConfirmedScheduleAssignmentStatus status
    );

    /**
     * 같은 time_detail에 같은 근무자가 이미 배정되어 있는지 확인한다.
     */
    boolean existsByTimeDetail_IdAndWorkerMemberIdAndStatusAndDeletedAtIsNull(
            Long timeDetailId,
            Long workerMemberId,
            ConfirmedScheduleAssignmentStatus status
    );

    /**
     * 여러 time_detail에 등록된 활성 배정 목록을 한 번에 조회한다.
     */
    List<ConfirmedScheduleAssignment> findByTimeDetail_IdInAndStatusAndDeletedAtIsNull(
            Collection<Long> timeDetailIds,
            ConfirmedScheduleAssignmentStatus status
    );

    /**
     * 확정 주간 스케줄의 특정 time_detail에 속한 활성 배정을 조회한다.
     */
    List<ConfirmedScheduleAssignment> findByConfirmedWeekSchedule_IdAndTimeDetail_IdAndStatusAndDeletedAtIsNullOrderByIdAsc(
            Long confirmedWeekScheduleId,
            Long timeDetailId,
            ConfirmedScheduleAssignmentStatus status
    );

    /**
     * 특정 근무자의 기간 내 활성 확정 근무 배정 목록을 달력 정렬 순서로 조회한다.
     */
    @Query("""
            select assignment
              from ConfirmedScheduleAssignment assignment
              join fetch assignment.confirmedWeekSchedule confirmedWeekSchedule
              join fetch assignment.weekSchedule weekSchedule
              join fetch assignment.day day
              join fetch assignment.timeDetail timeDetail
             where assignment.workerMemberId = :workerMemberId
               and day.date between :from and :to
               and assignment.status = :assignmentStatus
               and assignment.deletedAt is null
               and confirmedWeekSchedule.status = :confirmedWeekScheduleStatus
               and confirmedWeekSchedule.deletedAt is null
               and day.status = :dayStatus
               and day.deletedAt is null
               and timeDetail.status = :timeDetailStatus
               and timeDetail.deletedAt is null
             order by day.date asc, timeDetail.workPartNo asc, timeDetail.startTime asc, assignment.id asc
            """)
    List<ConfirmedScheduleAssignment> findActiveAssignmentsForWorkerCalendar(
            @Param("workerMemberId") Long workerMemberId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to,
            @Param("assignmentStatus") ConfirmedScheduleAssignmentStatus assignmentStatus,
            @Param("confirmedWeekScheduleStatus") ConfirmedWeekScheduleStatus confirmedWeekScheduleStatus,
            @Param("dayStatus") DayStatus dayStatus,
            @Param("timeDetailStatus") TimeDetailStatus timeDetailStatus
    );

    /**
     * 특정 사업장의 기간 내 활성 확정 근무 배정 목록을 주간 근무표 정렬 순서로 조회한다.
     */
    @Query("""
            select assignment
              from ConfirmedScheduleAssignment assignment
              join fetch assignment.confirmedWeekSchedule confirmedWeekSchedule
              join fetch assignment.weekSchedule weekSchedule
              join fetch assignment.day day
              join fetch assignment.timeDetail timeDetail
             where assignment.workPlaceId = :workPlaceId
               and day.date between :from and :to
               and assignment.status = :assignmentStatus
               and assignment.deletedAt is null
               and confirmedWeekSchedule.status = :confirmedWeekScheduleStatus
               and confirmedWeekSchedule.deletedAt is null
               and day.status = :dayStatus
               and day.deletedAt is null
               and timeDetail.status = :timeDetailStatus
               and timeDetail.deletedAt is null
             order by day.date asc, timeDetail.workPartNo asc, timeDetail.startTime asc, assignment.id asc
            """)
    List<ConfirmedScheduleAssignment> findActiveAssignmentsForOwnerWeeklySchedule(
            @Param("workPlaceId") Long workPlaceId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to,
            @Param("assignmentStatus") ConfirmedScheduleAssignmentStatus assignmentStatus,
            @Param("confirmedWeekScheduleStatus") ConfirmedWeekScheduleStatus confirmedWeekScheduleStatus,
            @Param("dayStatus") DayStatus dayStatus,
            @Param("timeDetailStatus") TimeDetailStatus timeDetailStatus
    );

    /**
     * 근무 변경 요청 검증에 필요한 확정 근무 배정을 연관 정보와 함께 조회한다.
     */
    @Query("""
            select assignment
              from ConfirmedScheduleAssignment assignment
              join fetch assignment.confirmedWeekSchedule confirmedWeekSchedule
              join fetch assignment.weekSchedule weekSchedule
              join fetch assignment.day day
              join fetch assignment.timeDetail timeDetail
             where assignment.id = :assignmentId
               and assignment.workPlaceId = :workPlaceId
               and assignment.status = :assignmentStatus
               and assignment.deletedAt is null
               and confirmedWeekSchedule.status = :confirmedWeekScheduleStatus
               and confirmedWeekSchedule.deletedAt is null
               and day.status = :dayStatus
               and day.deletedAt is null
               and timeDetail.status = :timeDetailStatus
               and timeDetail.deletedAt is null
            """)
    Optional<ConfirmedScheduleAssignment> findActiveAssignmentForWorkChange(
            @Param("assignmentId") Long assignmentId,
            @Param("workPlaceId") Long workPlaceId,
            @Param("assignmentStatus") ConfirmedScheduleAssignmentStatus assignmentStatus,
            @Param("confirmedWeekScheduleStatus") ConfirmedWeekScheduleStatus confirmedWeekScheduleStatus,
            @Param("dayStatus") DayStatus dayStatus,
            @Param("timeDetailStatus") TimeDetailStatus timeDetailStatus
    );

    /**
     * 대상 근무자가 같은 날짜와 겹치는 시간대에 이미 배정되어 있는지 확인한다.
     */
    @Query("""
            select assignment
              from ConfirmedScheduleAssignment assignment
              join fetch assignment.confirmedWeekSchedule confirmedWeekSchedule
              join fetch assignment.day day
              join fetch assignment.timeDetail timeDetail
             where assignment.workPlaceId = :workPlaceId
               and assignment.workerMemberId = :workerMemberId
               and assignment.status = :assignmentStatus
               and assignment.deletedAt is null
               and confirmedWeekSchedule.status = :confirmedWeekScheduleStatus
               and confirmedWeekSchedule.deletedAt is null
               and day.date = :workDate
               and day.status = :dayStatus
               and day.deletedAt is null
               and timeDetail.status = :timeDetailStatus
               and timeDetail.deletedAt is null
               and timeDetail.startTime < :closeTime
               and timeDetail.closeTime > :startTime
            """)
    List<ConfirmedScheduleAssignment> findOverlappingActiveAssignments(
            @Param("workPlaceId") Long workPlaceId,
            @Param("workerMemberId") Long workerMemberId,
            @Param("workDate") LocalDate workDate,
            @Param("startTime") LocalTime startTime,
            @Param("closeTime") LocalTime closeTime,
            @Param("assignmentStatus") ConfirmedScheduleAssignmentStatus assignmentStatus,
            @Param("confirmedWeekScheduleStatus") ConfirmedWeekScheduleStatus confirmedWeekScheduleStatus,
            @Param("dayStatus") DayStatus dayStatus,
            @Param("timeDetailStatus") TimeDetailStatus timeDetailStatus
    );
}
