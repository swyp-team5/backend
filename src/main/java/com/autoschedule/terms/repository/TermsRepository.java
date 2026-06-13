package com.autoschedule.terms.repository;

import com.autoschedule.terms.domain.Terms;
import com.autoschedule.terms.domain.TermsStatus;
import com.autoschedule.terms.domain.TermsType;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 약관 조회와 저장을 담당한다.
 */
public interface TermsRepository extends JpaRepository<Terms, Long> {

    /**
     * 지정한 약관 유형들의 활성 약관을 조회한다.
     */
    List<Terms> findByTermsTypeInAndStatus(Collection<TermsType> termsTypes, TermsStatus status);

    /**
     * 지정한 약관 유형들의 활성 약관을 회원가입 화면 표시 순서로 조회한다.
     */
    List<Terms> findByTermsTypeInAndStatusOrderByTermsTypeAscIdAsc(
            Collection<TermsType> termsTypes,
            TermsStatus status
    );
}
