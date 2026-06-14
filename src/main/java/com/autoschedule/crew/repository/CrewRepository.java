package com.autoschedule.crew.repository;

import com.autoschedule.crew.domain.Crew;
import com.autoschedule.crew.domain.CrewJoinStatus;
import com.autoschedule.crew.domain.CrewStatus;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 사업장 소속 정보의 저장과 조회를 담당한다.
 */
public interface CrewRepository extends JpaRepository<Crew, Long> {

    /**
     * 특정 회원이 이미 해당 사업장 크루로 등록되어 있는지 확인한다.
     */
    boolean existsByMember_IdAndWorkPlace_Id(Long memberId, Long workPlaceId);

    /**
     * 특정 회원이 승인되고 활성화된 사업장 크루인지 확인한다.
     */
    boolean existsByMember_IdAndWorkPlace_IdAndJoinStatusAndStatus(
            Long memberId,
            Long workPlaceId,
            CrewJoinStatus joinStatus,
            CrewStatus status
    );
}
