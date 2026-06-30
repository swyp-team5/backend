package com.autoschedule.workerselect.repository;

import com.autoschedule.workerselect.domain.WorkerUnavailableTimeDetail;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 근무 불가능한 time_detail 목록을 조회하고 저장한다.
 */
public interface WorkerUnavailableTimeDetailRepository extends JpaRepository<WorkerUnavailableTimeDetail, Long> {

    List<WorkerUnavailableTimeDetail> findBySubmission_Id(Long submissionId);
}