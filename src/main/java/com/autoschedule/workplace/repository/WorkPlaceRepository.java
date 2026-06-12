package com.autoschedule.workplace.repository;

import com.autoschedule.workplace.domain.WorkPlace;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 사업장 저장과 조회를 담당한다.
 */
public interface WorkPlaceRepository extends JpaRepository<WorkPlace, Long> {

    /**
     * 사장님이 소유한 사업장을 조회한다.
     */
    Optional<WorkPlace> findByIdAndOwnerMemberId(Long id, Long ownerMemberId);
}
