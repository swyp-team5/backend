package com.autoschedule.workplace.repository;

import com.autoschedule.workplace.domain.WorkPlace;
import com.autoschedule.workplace.domain.WorkPlaceStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 사업장 저장과 조회를 담당한다.
 */
public interface WorkPlaceRepository extends JpaRepository<WorkPlace, Long> {

    /**
     * 사장 ID, 사업장 ID, 상태, 삭제 시각 조건으로 사업장을 조회한다.
     */
    Optional<WorkPlace> findByIdAndOwnerMemberIdAndStatusAndDeletedAtIsNull(
            Long id,
            Long ownerMemberId,
            WorkPlaceStatus status
    );

    /**
     * 일반 사용자 API에서 사용할 사장 소유 활성 사업장 조회 메서드다.
     */
    default Optional<WorkPlace> findOwnedActiveById(Long id, Long ownerMemberId) {
        return findByIdAndOwnerMemberIdAndStatusAndDeletedAtIsNull(
                id,
                ownerMemberId,
                WorkPlaceStatus.ACTIVE
        );
    }

    /**
     * 사장님이 소유한 사업장을 조회한다.
     */
    Optional<WorkPlace> findByIdAndOwnerMemberId(Long id, Long ownerMemberId);

    /**
     * 사업장 ID와 상태, 삭제 시각 조건으로 사업장을 조회한다. (소유자 무관)
     */
    Optional<WorkPlace> findByIdAndStatusAndDeletedAtIsNull(Long id, WorkPlaceStatus status);

    /**
     * 사업장 ID 목록과 상태, 삭제 시각 조건으로 활성 사업장 목록을 한 번에 조회한다.
     */
    List<WorkPlace> findByIdInAndStatusAndDeletedAtIsNull(Collection<Long> ids, WorkPlaceStatus status);
}
