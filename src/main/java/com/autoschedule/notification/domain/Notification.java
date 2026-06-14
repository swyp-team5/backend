package com.autoschedule.notification.domain;

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
 * 회원 알림함에 노출되는 알림 데이터를 저장한다.
 */
@Getter
@Entity
@Table(name = "notification")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "notification_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "receiver_member_id", nullable = false)
    private Member receiverMember;

    @Enumerated(EnumType.STRING)
    @Column(name = "notification_type", nullable = false, length = 50)
    private NotificationType notificationType;

    @Enumerated(EnumType.STRING)
    @Column(name = "push_policy", nullable = false, length = 20)
    private PushPolicy pushPolicy;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(nullable = false, length = 500)
    private String body;

    @Column(columnDefinition = "json")
    private String data;

    @Column(name = "read_at")
    private LocalDateTime readAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NotificationStatus status;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    /**
     * 회원 알림함에 저장할 새 알림을 생성한다.
     */
    public static Notification create(
            Member receiverMember,
            NotificationType notificationType,
            PushPolicy pushPolicy,
            String title,
            String body,
            String data
    ) {
        Notification notification = new Notification();
        notification.receiverMember = receiverMember;
        notification.notificationType = notificationType;
        notification.pushPolicy = pushPolicy;
        notification.title = title;
        notification.body = body;
        notification.data = data;
        notification.status = NotificationStatus.ACTIVE;
        return notification;
    }

    /**
     * 알림을 읽음 상태로 변경한다.
     */
    public void markRead(LocalDateTime readAt) {
        if (this.readAt == null) {
            this.readAt = readAt;
        }
    }
}
