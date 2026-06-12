package com.autoschedule.crew.repository;

import com.autoschedule.crew.domain.CrewInvitation;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 크루 초대 코드의 저장, 조회, 동시성 잠금 조회를 담당한다.
 */
public interface CrewInvitationRepository extends JpaRepository<CrewInvitation, Long> {

    /**
     * 초대 코드 중복 생성을 피하기 위해 코드 존재 여부를 확인한다.
     */
    boolean existsByInviteCode(String inviteCode);

    /**
     * 초대 수락 시 하나의 코드가 동시에 여러 번 사용되지 않도록 쓰기 잠금으로 조회한다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select invitation from CrewInvitation invitation where invitation.inviteCode = :inviteCode")
    Optional<CrewInvitation> findByInviteCodeForUpdate(@Param("inviteCode") String inviteCode);
}
