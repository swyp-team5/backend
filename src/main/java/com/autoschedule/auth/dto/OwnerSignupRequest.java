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
 * 사장님 회원가입 완료에 필요한 소셜 인증 정보와 필수 입력값을 받는다.
 */
public record OwnerSignupRequest(
        @NotNull(message = "소셜 로그인 제공자는 필수입니다.") SocialProvider provider,
        String idToken,
        String accessToken,
        String authorizationCode,
        @NotBlank(message = "이름은 필수입니다.")
        @Size(max = 10, message = "이름은 10자 이하로 입력해주세요.")
        String name,
        @NotBlank(message = "휴대폰 번호는 필수입니다.")
        @Pattern(
                regexp = "^01\\d{9}$",
                message = "휴대폰 번호는 하이픈 없는 11자리 숫자로 입력해주세요."
        ) String phoneNumber,
        @Valid @NotEmpty(message = "약관 동의 목록은 필수입니다.") List<TermsAgreementRequest> termsAgreements,
        @Valid @NotNull(message = "사업장 정보는 필수입니다.") WorkPlaceSignupRequest workPlace,
        @Valid @NotNull(message = "기기 정보는 필수입니다.") DeviceRequest device
) {
}
