package com.autoschedule.notification.repository;

import com.autoschedule.notification.domain.MemberNotificationSetting;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 회원 알림 수신 설정 저장과 조회를 담당한다.
 */
public interface MemberNotificationSettingRepository extends JpaRepository<MemberNotificationSetting, Long> {

    /**
     * 회원 ID로 알림 수신 설정을 조회한다.
     */
    Optional<MemberNotificationSetting> findByMember_Id(Long memberId);

    /**
     * 여러 회원의 알림 수신 설정을 한 번에 조회한다.
     */
    @EntityGraph(attributePaths = "member")
    List<MemberNotificationSetting> findByMember_IdIn(Collection<Long> memberIds);
}
