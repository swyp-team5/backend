package com.autoschedule.workplace.repository;

import com.autoschedule.workplace.domain.WorkPlace;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 사업장 저장과 조회를 담당한다.
 */
public interface WorkPlaceRepository extends JpaRepository<WorkPlace, Long> {
}
