package com.autoschedule.workerselect.repository;

import com.autoschedule.workerselect.domain.WorkerUnavailable;
import com.autoschedule.workerselect.domain.WorkerUnavailableStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

/**
 * 근무자 불가능 근무 타임 제출 정보를 조회하고 저장한다.
 */
public interface WorkerUnavailableRepository extends JpaRepository<WorkerUnavailable, Long> {

    /**
     * 회원이 특정 사업장의 주간 스케줄에 이미 근무 불가 정보를 제출했는지 확인한다.
     */
    boolean existsByMemberIdAndWorkPlaceIdAndWeekScheduleIdAndStatusAndDeletedAtIsNull(
            Long memberId,
            Long workPlaceId,
            Long weekScheduleId,
            WorkerUnavailableStatus status
    );

    /**
     * 회원이 제출한 활성 상태의 근무 불가 타임 목록을 조회한다.
     */
    List<WorkerUnavailable> findByMemberIdAndStatusAndDeletedAtIsNull(
            Long memberId,
            WorkerUnavailableStatus status
    );

    /**
     * 여러 회원 중 근무 불가 정보를 제출한 회원 목록을 조회한다.
     */
    List<WorkerUnavailable> findByMemberIdInAndStatusAndDeletedAtIsNull(
            Collection<Long> memberIds,
            WorkerUnavailableStatus status
    );

    /**
     * 요청값으로 받은 timeDetaildId 목록을 한번에 조회한다.
     */
    @Query("SELECT wu.timeDetail.id FROM WorkerUnavailable wu " +
            "WHERE wu.memberId = :memberId " +
            "AND wu.timeDetail.id IN :timeDetailIds " +
            "AND wu.status = :status " +
            "AND wu.deletedAt IS NULL")
    List<Long> findTimeDetailIdsByMemberIdAndTimeDetail_IdInAndStatusAndDeletedAtIsNull(
            @Param("memberId") Long memberId,
            @Param("timeDetailIds") List<Long> timeDetailIds,
            @Param("status") WorkerUnavailableStatus status
    );

}