package com.autoschedule.notification.repository;

import com.autoschedule.notification.domain.FcmToken;
import com.autoschedule.notification.domain.FcmTokenStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * FCM 토큰 저장과 조회를 담당한다.
 */
public interface FcmTokenRepository extends JpaRepository<FcmToken, Long> {

    /**
     * 회원과 기기 ID로 FCM 토큰을 조회한다.
     */
    Optional<FcmToken> findByMember_IdAndDeviceId(Long memberId, String deviceId);

    /**
     * 회원의 특정 상태 FCM 토큰 목록을 조회한다.
     */
    List<FcmToken> findByMember_IdAndStatus(Long memberId, FcmTokenStatus status);
}
