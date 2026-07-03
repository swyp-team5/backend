package com.autoschedule.workerselect.repository;

import com.autoschedule.workerselect.domain.WorkerUnavailableTimeDetail;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

/**
 * 근무 불가능한 time_detail 목록을 조회하고 저장한다.
 */
public interface WorkerUnavailableTimeDetailRepository extends JpaRepository<WorkerUnavailableTimeDetail, Long> {

    List<WorkerUnavailableTimeDetail> findBySubmission_Id(Long submissionId);

    /**
     * 여러 제출 이력에 속한 근무 불가 time_detail 목록을 한 번에 조회한다.
     */
    List<WorkerUnavailableTimeDetail> findBySubmission_IdIn(Collection<Long> submissionIds);
}
