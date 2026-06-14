package com.autoschedule.notification.dto;

import com.autoschedule.auth.domain.DevicePlatform;
import com.autoschedule.notification.domain.FcmToken;
import com.autoschedule.notification.domain.FcmTokenStatus;
import java.time.LocalDateTime;

/**
 * FCM 토큰 등록 또는 갱신 응답을 표현한다.
 */
public record FcmTokenResponse(
        Long fcmTokenId,
        String deviceId,
        DevicePlatform platform,
        String appVersion,
        FcmTokenStatus status,
        LocalDateTime lastRegisteredAt
) {

    /**
     * FCM 토큰 엔티티를 모바일 응답으로 변환한다.
     */
    public static FcmTokenResponse from(FcmToken fcmToken) {
        return new FcmTokenResponse(
                fcmToken.getId(),
                fcmToken.getDeviceId(),
                fcmToken.getPlatform(),
                fcmToken.getAppVersion(),
                fcmToken.getStatus(),
                fcmToken.getLastRegisteredAt()
        );
    }
}
