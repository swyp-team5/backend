package com.autoschedule.workerselect.repository;

import com.autoschedule.workerselect.domain.WorkerSelectSubmission;
import com.autoschedule.workerselect.domain.WorkerSelectSubmissionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 근무자의 근무 불가 스케줄 제출 현황을 조회하고 저장한다.
 */
public interface WorkerSelectSubmissionRepository extends JpaRepository<WorkerSelectSubmission, Long> {

    /**
     * 동일 사업장, 주간 스케줄, 회원 조합으로 이미 제출했는지 확인한다.
     */
    boolean existsByWorkPlaceIdAndWeekScheduleIdAndMemberIdAndStatusAndDeletedAtIsNull(
            Long workPlaceId,
            Long weekScheduleId,
            Long memberId,
            WorkerSelectSubmissionStatus status
    );

    /**
     * 특정 사업장과 주간 스케줄에서 제출한 회원 목록을 조회한다.
     */
    List<WorkerSelectSubmission> findByWorkPlaceIdAndWeekScheduleIdAndMemberIdInAndStatusAndDeletedAtIsNull(
            Long workPlaceId,
            Long weekScheduleId,
            List<Long> memberIds,
            WorkerSelectSubmissionStatus status
    );

    /**
     * 특정 사업장과 주간 스케줄에 제출된 활성 근무 불가 제출 목록을 조회한다.
     */
    List<WorkerSelectSubmission> findByWorkPlaceIdAndWeekScheduleIdAndStatusAndDeletedAtIsNull(
            Long workPlaceId,
            Long weekScheduleId,
            WorkerSelectSubmissionStatus status
    );
}
