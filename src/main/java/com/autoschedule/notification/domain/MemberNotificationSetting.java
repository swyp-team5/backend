package com.autoschedule.notification.domain;

import com.autoschedule.global.domain.BaseEntity;
import com.autoschedule.member.domain.Member;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 회원의 알림 수신 설정을 저장한다.
 */
@Getter
@Entity
@Table(name = "member_notification_setting")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MemberNotificationSetting extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "member_notification_setting_id")
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Column(name = "fcm_push_enabled", nullable = false)
    private boolean fcmPushEnabled;

    /**
     * 신규 회원 또는 기존 회원의 기본 알림 설정을 생성한다.
     */
    public static MemberNotificationSetting createDefault(Member member) {
        MemberNotificationSetting setting = new MemberNotificationSetting();
        setting.member = member;
        setting.fcmPushEnabled = true;
        return setting;
    }

    /**
     * FCM 푸시 수신 여부를 변경한다.
     */
    public void updateFcmPushEnabled(boolean fcmPushEnabled) {
        this.fcmPushEnabled = fcmPushEnabled;
    }
}
