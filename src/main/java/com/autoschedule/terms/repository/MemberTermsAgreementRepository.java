package com.autoschedule.terms.repository;

import com.autoschedule.terms.domain.MemberTermsAgreement;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 회원 약관 동의 이력 저장과 조회를 담당한다.
 */
public interface MemberTermsAgreementRepository extends JpaRepository<MemberTermsAgreement, Long> {
}
