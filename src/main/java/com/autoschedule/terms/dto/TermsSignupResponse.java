package com.autoschedule.terms.dto;

import com.autoschedule.terms.domain.Terms;
import com.autoschedule.terms.domain.TermsType;
import java.util.List;

/**
 * 회원가입 화면에서 표시할 약관 목록 응답이다.
 */
public record TermsSignupResponse(
        List<TermsItem> terms
) {

    /**
     * 조회된 약관 엔티티 목록을 API 응답 DTO로 변환한다.
     */
    public static TermsSignupResponse from(List<Terms> terms) {
        return new TermsSignupResponse(
                terms.stream()
                        .map(TermsItem::from)
                        .toList()
        );
    }

    /**
     * 회원가입 화면에 표시할 개별 약관 항목이다.
     */
    public record TermsItem(
            Long termsId,
            TermsType termsType,
            String title,
            boolean required,
            String version,
            String content
    ) {

        /**
         * 약관 엔티티를 클라이언트 응답 항목으로 변환한다.
         */
        public static TermsItem from(Terms terms) {
            return new TermsItem(
                    terms.getId(),
                    terms.getTermsType(),
                    terms.getTitle(),
                    terms.isRequired(),
                    terms.getVersion(),
                    terms.getContent()
            );
        }
    }
}
