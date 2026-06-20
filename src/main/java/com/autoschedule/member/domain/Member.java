package com.autoschedule.member.domain;

import com.autoschedule.global.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 소셜 인증으로 가입한 서비스 회원 정보를 저장한다.
 */
@Getter
@Entity
@Table(name = "member")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Member extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "member_id")
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "social_provider", nullable = false, length = 20)
    private SocialProvider socialProvider;

    @Column(name = "social_subject", nullable = false, length = 255)
    private String socialSubject;

    @Column(name = "social_email", length = 255)
    private String socialEmail;

    @Column(nullable = false, length = 10)
    private String name;

    @Column(name = "phone_number", nullable = false, length = 11, columnDefinition = "char(11)")
    private String phoneNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MemberRole role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MemberStatus status;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    /**
     * 회원가입 완료 시점에 활성 회원을 생성한다.
     */
    public static Member create(
            SocialProvider socialProvider,
            String socialSubject,
            String socialEmail,
            String name,
            String phoneNumber,
            MemberRole role
    ) {
        Member member = new Member();
        member.socialProvider = socialProvider;
        member.socialSubject = socialSubject;
        member.socialEmail = socialEmail;
        member.name = name;
        member.phoneNumber = phoneNumber;
        member.role = role;
        member.status = MemberStatus.ACTIVE;
        return member;
    }

    /**
     * 회원탈퇴를 신청해 30일 유예 상태로 전환하고 최초 신청 시각을 저장한다.
     */
    public void requestWithdrawal(LocalDateTime requestedAt) {
        if (status == MemberStatus.WITHDRAWAL_PENDING) {
            return;
        }

        status = MemberStatus.WITHDRAWAL_PENDING;
        deletedAt = requestedAt;
    }

    /**
     * 회원탈퇴 유예 상태를 취소하고 정상 회원 상태로 복구한다.
     */
    public void cancelWithdrawal() {
        if (status == MemberStatus.ACTIVE) {
            return;
        }

        status = MemberStatus.ACTIVE;
        deletedAt = null;
    }

    /**
     * 현재 회원이 탈퇴 유예 상태인지 확인한다.
     */
    public boolean isWithdrawalPending() {
        return status == MemberStatus.WITHDRAWAL_PENDING;
    }

    /**
     * 탈퇴 신청 시각 기준으로 사용자가 직접 탈퇴를 취소할 수 있는 기간인지 확인한다.
     */
    public boolean isWithinWithdrawalGracePeriod(LocalDateTime now, Duration gracePeriod) {
        if (!isWithdrawalPending() || deletedAt == null) {
            return false;
        }
        return !deletedAt.plus(gracePeriod).isBefore(now);
    }

    /**
     * 회원 본인의 프로필 기본 정보를 수정한다.
     */
    public void updateProfile(String name, String phoneNumber) {
        this.name = name;
        this.phoneNumber = phoneNumber;
    }

}
