package com.autoschedule.notification.domain;

import com.autoschedule.auth.domain.DevicePlatform;
import com.autoschedule.global.domain.BaseEntity;
import com.autoschedule.member.domain.Member;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 회원의 기기별 FCM 토큰을 저장한다.
 */
@Getter
@Entity
@Table(name = "fcm_token")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FcmToken extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "fcm_token_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Column(name = "device_id", nullable = false, length = 100)
    private String deviceId;

    @Column(nullable = false, length = 512)
    private String token;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DevicePlatform platform;

    @Column(name = "app_version", nullable = false, length = 30)
    private String appVersion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FcmTokenStatus status;

    @Column(name = "last_registered_at", nullable = false)
    private LocalDateTime lastRegisteredAt;

    @Column(name = "last_used_at")
    private LocalDateTime lastUsedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    /**
     * 회원의 새 기기 FCM 토큰을 생성한다.
     */
    public static FcmToken create(
            Member member,
            String deviceId,
            String token,
            DevicePlatform platform,
            String appVersion,
            LocalDateTime registeredAt
    ) {
        FcmToken fcmToken = new FcmToken();
        fcmToken.member = member;
        fcmToken.deviceId = deviceId;
        fcmToken.token = token;
        fcmToken.platform = platform;
        fcmToken.appVersion = appVersion;
        fcmToken.status = FcmTokenStatus.ACTIVE;
        fcmToken.lastRegisteredAt = registeredAt;
        return fcmToken;
    }

    /**
     * 같은 기기의 최신 FCM 토큰 정보로 갱신한다.
     */
    public void updateToken(
            String token,
            DevicePlatform platform,
            String appVersion,
            LocalDateTime registeredAt
    ) {
        this.token = token;
        this.platform = platform;
        this.appVersion = appVersion;
        this.status = FcmTokenStatus.ACTIVE;
        this.lastRegisteredAt = registeredAt;
        this.deletedAt = null;
    }

    /**
     * 토큰을 사용한 시각을 기록한다.
     */
    public void markUsed(LocalDateTime usedAt) {
        this.lastUsedAt = usedAt;
    }

    /**
     * FCM 토큰을 비활성 상태로 변경한다.
     */
    public void deactivate(LocalDateTime deletedAt) {
        this.status = FcmTokenStatus.INACTIVE;
        this.deletedAt = deletedAt;
    }
}
