package com.autoschedule.terms.service;

import com.autoschedule.member.domain.MemberRole;
import com.autoschedule.terms.domain.Terms;
import com.autoschedule.terms.domain.TermsStatus;
import com.autoschedule.terms.domain.TermsType;
import com.autoschedule.terms.dto.TermsSignupResponse;
import com.autoschedule.terms.repository.TermsRepository;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 약관 조회 유스케이스를 처리한다.
 */
@Service
@RequiredArgsConstructor
public class TermsQueryService {

    private final TermsRepository termsRepository;
    private final SignupTermsPolicy signupTermsPolicy;

    /**
     * 회원가입 역할에 필요한 활성 약관 목록을 조회한다.
     */
    @Transactional(readOnly = true)
    public TermsSignupResponse getSignupTerms(MemberRole role) {
        Set<TermsType> termsTypes = signupTermsPolicy.resolve(role);
        List<Terms> terms = termsRepository.findByTermsTypeInAndStatusOrderByTermsTypeAscIdAsc(
                termsTypes,
                TermsStatus.ACTIVE
        );
        return TermsSignupResponse.from(terms);
    }
}
