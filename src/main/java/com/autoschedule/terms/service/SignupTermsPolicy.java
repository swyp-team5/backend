package com.autoschedule.terms.service;

import com.autoschedule.member.domain.MemberRole;
import com.autoschedule.terms.domain.TermsType;
import java.util.EnumSet;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * 회원가입 역할별로 적용해야 하는 약관 유형 조합을 결정한다.
 */
@Component
public class SignupTermsPolicy {

    /**
     * 회원가입 역할에 필요한 공통 약관과 역할별 약관 유형을 반환한다.
     */
    public Set<TermsType> resolve(MemberRole role) {
        return switch (role) {
            case OWNER -> EnumSet.of(TermsType.COMMON, TermsType.OWNER);
            case WORKER -> EnumSet.of(TermsType.COMMON, TermsType.WORKER);
        };
    }
}
