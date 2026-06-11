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
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 소셜 인증으로 가입한 서비스 회원 정보를 저장한다.
 */
@Getter
@Entity
@Table(
        name = "member",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_member_social_provider_subject",
                        columnNames = {"social_provider", "social_subject"}
                )
        }
)
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

}
