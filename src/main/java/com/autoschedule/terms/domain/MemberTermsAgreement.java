package com.autoschedule.terms.domain;

import com.autoschedule.global.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * 회원이 특정 약관 버전에 동의하거나 미동의한 이력을 저장한다.
 */
@Getter
@Entity
@Table(name = "member_terms_agreement")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MemberTermsAgreement extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "member_terms_agreement_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "terms_id", nullable = false)
    private Terms terms;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(nullable = false)
    private boolean agreed;

    @Column(name = "agreed_at", nullable = false)
    private LocalDateTime agreedAt;

    /**
     * 회원가입 요청에서 전달된 약관 동의 상태를 이력으로 생성한다.
     */
    public static MemberTermsAgreement create(Long memberId, Terms terms, boolean agreed) {
        MemberTermsAgreement agreement = new MemberTermsAgreement();
        agreement.memberId = memberId;
        agreement.terms = terms;
        agreement.agreed = agreed;
        agreement.agreedAt = LocalDateTime.now();
        return agreement;
    }


}
