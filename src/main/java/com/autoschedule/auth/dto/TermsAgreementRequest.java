package com.autoschedule.auth.dto;

import jakarta.validation.constraints.NotNull;

/**
 * 회원가입 시 사용자가 선택한 약관 동의 상태를 받는다.
 */
public record TermsAgreementRequest(
        @NotNull(message = "약관 ID는 필수입니다.") Long termsId,
        boolean agreed
) {
}
