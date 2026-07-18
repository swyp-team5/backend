package com.autoschedule.workchange.repository;

import com.autoschedule.workchange.domain.WorkChangeRequest;
import com.autoschedule.workchange.domain.WorkChangeRequestStatus;
import java.util.Collection;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 교대/대타 요청의 저장과 조회를 담당한다.
 */
public interface WorkChangeRequestRepository extends JpaRepository<WorkChangeRequest, Long> {

    /**
     * 같은 근무 배정에 대한 처리 중인 요청이 이미 있는지 확인한다.
     */
    boolean existsByRequestAssignment_IdAndStatusInAndDeletedAtIsNull(
            Long requestAssignmentId,
            Collection<WorkChangeRequestStatus> statuses
    );

    /**
     * 대상 근무 배정에 대한 처리 중인 요청이 이미 있는지 확인한다.
     */
    boolean existsByTargetAssignment_IdAndStatusInAndDeletedAtIsNull(
            Long targetAssignmentId,
            Collection<WorkChangeRequestStatus> statuses
    );

    /**
     * 교대/대타 요청을 확정 근무 배정과 함께 조회한다.
     */
    @Query("""
            select request
              from WorkChangeRequest request
              join fetch request.requestAssignment requestAssignment
              left join fetch request.targetAssignment targetAssignment
             where request.id = :requestId
               and request.workPlaceId = :workPlaceId
               and request.deletedAt is null
            """)
    Optional<WorkChangeRequest> findByIdAndWorkPlaceIdWithAssignments(
            @Param("requestId") Long requestId,
            @Param("workPlaceId") Long workPlaceId
    );

    /**
     * 근무자가 보낸 요청 목록을 최신순으로 조회한다.
     */
    Page<WorkChangeRequest> findByWorkPlaceIdAndRequesterMemberIdAndDeletedAtIsNull(
            Long workPlaceId,
            Long requesterMemberId,
            Pageable pageable
    );

    /**
     * 근무자가 받은 요청 목록을 최신순으로 조회한다.
     */
    Page<WorkChangeRequest> findByWorkPlaceIdAndTargetMemberIdAndDeletedAtIsNull(
            Long workPlaceId,
            Long targetMemberId,
            Pageable pageable
    );

    /**
     * 근무자가 보내거나 받은 요청 목록을 최신순으로 조회한다.
     */
    @Query("""
            select request
              from WorkChangeRequest request
             where request.workPlaceId = :workPlaceId
               and request.deletedAt is null
               and (request.requesterMemberId = :memberId or request.targetMemberId = :memberId)
            """)
    Page<WorkChangeRequest> findWorkerRelatedRequests(
            @Param("workPlaceId") Long workPlaceId,
            @Param("memberId") Long memberId,
            Pageable pageable
    );

    /**
     * 사장님 사업장의 전체 교대/대타 요청 목록을 최신순으로 조회한다.
     */
    Page<WorkChangeRequest> findByWorkPlaceIdAndDeletedAtIsNull(Long workPlaceId, Pageable pageable);
}
