package com.autoschedule.auth.dto;

import com.autoschedule.member.domain.SocialProvider;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * 근무자 회원가입 완료에 필요한 소셜 인증 정보와 필수 입력값을 받는다.
 */
public record WorkerSignupRequest(
        @NotNull SocialProvider provider,
        String idToken,
        String accessToken,
        String authorizationCode,
        @NotBlank @Size(max = 10) String name,
        @NotBlank @Pattern(
                regexp = "^01\\d{9}$",
                message = "휴대폰 번호는 하이픈 없는 11자리 숫자여야 합니다."
        ) String phoneNumber,
        @Valid @NotEmpty List<TermsAgreementRequest> termsAgreements,
        @Valid @NotNull DeviceRequest device
) {
}
