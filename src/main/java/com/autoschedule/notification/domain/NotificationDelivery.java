package com.autoschedule.notification.domain;

import com.autoschedule.global.domain.BaseEntity;
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
 * FCM 앱 푸시 발송 시도와 결과 이력을 저장한다.
 */
@Getter
@Entity
@Table(name = "notification_delivery")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NotificationDelivery extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "notification_delivery_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "notification_id", nullable = false)
    private Notification notification;

    @Column(name = "fcm_token_id")
    private Long fcmTokenId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NotificationDeliveryChannel channel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NotificationDeliveryStatus status;

    @Column(name = "provider_message_id", length = 255)
    private String providerMessageId;

    @Column(name = "error_code", length = 100)
    private String errorCode;

    @Column(name = "error_message", length = 500)
    private String errorMessage;

    @Column(name = "attempted_at")
    private LocalDateTime attemptedAt;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    /**
     * FCM 토큰에 대한 발송 대기 이력을 생성한다.
     */
    public static NotificationDelivery createFcmPending(
            Notification notification,
            Long fcmTokenId,
            LocalDateTime attemptedAt
    ) {
        NotificationDelivery delivery = new NotificationDelivery();
        delivery.notification = notification;
        delivery.fcmTokenId = fcmTokenId;
        delivery.channel = NotificationDeliveryChannel.FCM;
        delivery.attemptedAt = attemptedAt;
        delivery.status = NotificationDeliveryStatus.PENDING;
        return delivery;
    }

    /**
     * FCM 발송을 시도한 시각을 기록한다.
     */
    public void markAttempted(LocalDateTime attemptedAt) {
        this.attemptedAt = attemptedAt;
    }

    /**
     * FCM 발송 성공 결과를 기록한다.
     */
    public void markSuccess(String providerMessageId, LocalDateTime sentAt) {
        this.status = NotificationDeliveryStatus.SUCCESS;
        this.providerMessageId = providerMessageId;
        this.sentAt = sentAt;
        this.errorCode = null;
        this.errorMessage = null;
    }

    /**
     * FCM 발송 실패 결과를 기록한다.
     */
    public void markFailure(String errorCode, String errorMessage) {
        this.status = NotificationDeliveryStatus.FAILED;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
    }
}
