package com.autoschedule.crew.repository;

import com.autoschedule.crew.domain.Crew;
import com.autoschedule.crew.domain.CrewJoinStatus;
import com.autoschedule.crew.domain.CrewRole;
import com.autoschedule.crew.domain.CrewStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 사업장 크루 소속 정보의 저장과 조회를 담당한다.
 */
public interface CrewRepository extends JpaRepository<Crew, Long> {

    /**
     * 특정 회원이 특정 사업장에 특정 상태의 크루로 등록되어 있는지 확인한다.
     */
    boolean existsByMember_IdAndWorkPlace_IdAndStatus(Long memberId, Long workPlaceId, CrewStatus status);

    /**
     * 일반 사용자 API에서 사용할 활성 크루 소속 존재 여부 확인 메서드다.
     */
    default boolean existsActiveByMemberIdAndWorkPlaceId(Long memberId, Long workPlaceId) {
        return existsByMember_IdAndWorkPlace_IdAndStatus(memberId, workPlaceId, CrewStatus.ACTIVE);
    }

    /**
     * 특정 회원이 승인되고 활성화된 사업장 크루인지 확인한다.
     */
    boolean existsByMember_IdAndWorkPlace_IdAndJoinStatusAndStatus(
            Long memberId,
            Long workPlaceId,
            CrewJoinStatus joinStatus,
            CrewStatus status
    );

    /**
     * 특정 사업장에 특정 상태로 등록된 크루 목록을 조회한다. -> 불가능 일자 제출한 근무자 목록 뽑을때 필요.
     */
    List<Crew> findByWorkPlace_IdAndCrewRoleAndStatus(
            Long workPlaceId,
            CrewRole crewRole,
            CrewStatus status
    );

    /**
     * 특정 사업장에 승인되고 활성화된 역할별 크루 회원 ID를 조회한다.
     */
    @Query("""
            select crew.member.id
              from Crew crew
             where crew.workPlace.id = :workPlaceId
               and crew.joinStatus = :joinStatus
               and crew.crewRole = :crewRole
               and crew.status = :status
            """)
    List<Long> findMemberIdsByWorkPlaceAndRole(
            @Param("workPlaceId") Long workPlaceId,
            @Param("joinStatus") CrewJoinStatus joinStatus,
            @Param("crewRole") CrewRole crewRole,
            @Param("status") CrewStatus status
    );
}
