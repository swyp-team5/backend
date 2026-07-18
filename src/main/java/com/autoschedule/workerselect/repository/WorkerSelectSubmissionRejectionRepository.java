package com.autoschedule.workerselect.repository;

import com.autoschedule.workerselect.domain.WorkerSelectSubmissionRejection;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 근무 불가 제출 반려 이력을 저장하고 조회한다.
 */
public interface WorkerSelectSubmissionRejectionRepository extends JpaRepository<WorkerSelectSubmissionRejection, Long> {
}
