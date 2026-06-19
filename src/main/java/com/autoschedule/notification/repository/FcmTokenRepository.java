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
     * FCM 토큰 ID와 상태로 토큰을 조회한다.
     */
    Optional<FcmToken> findByIdAndStatus(Long id, FcmTokenStatus status);

    /**
     * 회원 ID, 기기 ID, 상태로 FCM 토큰을 조회한다.
     */
    Optional<FcmToken> findByMember_IdAndDeviceIdAndStatus(
            Long memberId,
            String deviceId,
            FcmTokenStatus status
    );

    /**
     * 일반 사용자 API에서 사용할 활성 FCM 토큰 단건 조회 메서드다.
     */
    default Optional<FcmToken> findActiveById(Long id) {
        return findByIdAndStatus(id, FcmTokenStatus.ACTIVE);
    }

    /**
     * 일반 사용자 API에서 사용할 회원 기기별 활성 FCM 토큰 조회 메서드다.
     */
    default Optional<FcmToken> findActiveByMemberIdAndDeviceId(Long memberId, String deviceId) {
        return findByMember_IdAndDeviceIdAndStatus(memberId, deviceId, FcmTokenStatus.ACTIVE);
    }

    /**
     * 회원과 기기 ID로 FCM 토큰을 조회한다.
     */
    Optional<FcmToken> findByMember_IdAndDeviceId(Long memberId, String deviceId);

    /**
     * 회원의 특정 상태 FCM 토큰 목록을 조회한다.
     */
    List<FcmToken> findByMember_IdAndStatus(Long memberId, FcmTokenStatus status);
}
