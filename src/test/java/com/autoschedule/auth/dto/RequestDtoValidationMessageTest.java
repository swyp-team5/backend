package com.autoschedule.auth.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.autoschedule.auth.domain.DevicePlatform;
import com.autoschedule.member.domain.SocialProvider;
import com.autoschedule.workplace.domain.WorkPlaceSize;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 요청 DTO의 Bean Validation 메시지가 모바일 클라이언트에서 활용 가능한 한글 메시지로 고정되는지 검증한다.
 */
class RequestDtoValidationMessageTest {

    private Validator validator;

    /**
     * 각 테스트가 표준 Bean Validation 구현체로 DTO를 직접 검증하도록 Validator를 초기화한다.
     */
    @BeforeEach
    void setUp() {
        ValidatorFactory validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    /**
     * 소셜 로그인 요청에서 제공자와 기기 정보가 누락되면 명확한 필수 메시지를 반환한다.
     */
    @Test
    void socialLoginRequestHasFieldValidationMessages() {
        SocialLoginRequest request = new SocialLoginRequest(null, null, null, null, null);

        Set<ConstraintViolation<SocialLoginRequest>> violations = validator.validate(request);

        assertViolation(violations, "provider", "소셜 로그인 제공자는 필수입니다.");
        assertViolation(violations, "device", "기기 정보는 필수입니다.");
    }

    /**
     * 기기 정보 요청에서 필수값과 길이 제한을 위반하면 클라이언트 표시용 메시지를 반환한다.
     */
    @Test
    void deviceRequestHasFieldValidationMessages() {
        DeviceRequest request = new DeviceRequest("", null, "");

        Set<ConstraintViolation<DeviceRequest>> violations = validator.validate(request);

        assertViolation(violations, "deviceId", "기기 ID는 필수입니다.");
        assertViolation(violations, "platform", "기기 플랫폼은 필수입니다.");
        assertViolation(violations, "appVersion", "앱 버전은 필수입니다.");

        DeviceRequest tooLongRequest = new DeviceRequest("d".repeat(101), DevicePlatform.ANDROID, "1".repeat(31));
        Set<ConstraintViolation<DeviceRequest>> lengthViolations = validator.validate(tooLongRequest);

        assertViolation(lengthViolations, "deviceId", "기기 ID는 100자 이하로 입력해주세요.");
        assertViolation(lengthViolations, "appVersion", "앱 버전은 30자 이하로 입력해주세요.");
    }

    /**
     * 사장님 회원가입 요청에서 공통 필수값, 약관, 사업장, 기기 정보 검증 메시지를 반환한다.
     */
    @Test
    void ownerSignupRequestHasFieldValidationMessages() {
        OwnerSignupRequest request = new OwnerSignupRequest(
                null,
                null,
                null,
                null,
                "",
                "010-0000-0000",
                List.of(),
                null,
                null
        );

        Set<ConstraintViolation<OwnerSignupRequest>> violations = validator.validate(request);

        assertViolation(violations, "provider", "소셜 로그인 제공자는 필수입니다.");
        assertViolation(violations, "name", "이름은 필수입니다.");
        assertViolation(violations, "phoneNumber", "휴대폰 번호는 하이픈 없는 11자리 숫자로 입력해주세요.");
        assertViolation(violations, "termsAgreements", "약관 동의 목록은 필수입니다.");
        assertViolation(violations, "workPlace", "사업장 정보는 필수입니다.");
        assertViolation(violations, "device", "기기 정보는 필수입니다.");
    }

    /**
     * 근무자 회원가입 요청에서 공통 필수값과 약관, 기기 정보 검증 메시지를 반환한다.
     */
    @Test
    void workerSignupRequestHasFieldValidationMessages() {
        WorkerSignupRequest request = new WorkerSignupRequest(
                null,
                null,
                null,
                null,
                "이름이열자를초과합니다",
                "",
                List.of(),
                null
        );

        Set<ConstraintViolation<WorkerSignupRequest>> violations = validator.validate(request);

        assertViolation(violations, "provider", "소셜 로그인 제공자는 필수입니다.");
        assertViolation(violations, "name", "이름은 10자 이하로 입력해주세요.");
        assertViolation(violations, "phoneNumber", "휴대폰 번호는 필수입니다.");
        assertViolation(violations, "termsAgreements", "약관 동의 목록은 필수입니다.");
        assertViolation(violations, "device", "기기 정보는 필수입니다.");
    }

    /**
     * 약관 동의 요청에서 약관 ID가 누락되면 약관 식별자 필수 메시지를 반환한다.
     */
    @Test
    void termsAgreementRequestHasFieldValidationMessages() {
        TermsAgreementRequest request = new TermsAgreementRequest(null, true);

        Set<ConstraintViolation<TermsAgreementRequest>> violations = validator.validate(request);

        assertViolation(violations, "termsId", "약관 ID는 필수입니다.");
    }

    /**
     * 사업장 회원가입 요청에서 필수값과 DB 길이 제한에 맞는 검증 메시지를 반환한다.
     */
    @Test
    void workPlaceSignupRequestHasFieldValidationMessages() {
        WorkPlaceSignupRequest request = new WorkPlaceSignupRequest(null, "", "", "상세주소".repeat(26));

        Set<ConstraintViolation<WorkPlaceSignupRequest>> violations = validator.validate(request);

        assertViolation(violations, "size", "사업장 규모는 필수입니다.");
        assertViolation(violations, "name", "사업장 이름은 필수입니다.");
        assertViolation(violations, "roadAddress", "사업장 도로명 주소는 필수입니다.");
        assertViolation(violations, "detailAddress", "사업장 상세 주소는 100자 이하로 입력해주세요.");

        WorkPlaceSignupRequest tooLongRequest = new WorkPlaceSignupRequest(
                WorkPlaceSize.FIVE_TO_NINE,
                "상호명".repeat(34),
                "도로명주소".repeat(52),
                null
        );
        Set<ConstraintViolation<WorkPlaceSignupRequest>> lengthViolations = validator.validate(tooLongRequest);

        assertViolation(lengthViolations, "name", "사업장 이름은 100자 이하로 입력해주세요.");
        assertViolation(lengthViolations, "roadAddress", "사업장 도로명 주소는 255자 이하로 입력해주세요.");
    }

    /**
     * 토큰 재발급과 로그아웃 요청에서 토큰과 기기 ID 필수 메시지를 반환한다.
     */
    @Test
    void refreshTokenRequestHasFieldValidationMessages() {
        RefreshTokenRequest request = new RefreshTokenRequest("", "");

        Set<ConstraintViolation<RefreshTokenRequest>> violations = validator.validate(request);

        assertViolation(violations, "refreshToken", "리프레시 토큰은 필수입니다.");
        assertViolation(violations, "deviceId", "기기 ID는 필수입니다.");

        RefreshTokenRequest tooLongRequest = new RefreshTokenRequest("refresh-token", "d".repeat(101));
        Set<ConstraintViolation<RefreshTokenRequest>> lengthViolations = validator.validate(tooLongRequest);

        assertViolation(lengthViolations, "deviceId", "기기 ID는 100자 이하로 입력해주세요.");
    }

    /**
     * 검증 실패 목록에 특정 필드와 메시지가 포함되어 있는지 확인한다.
     */
    private <T> void assertViolation(Set<ConstraintViolation<T>> violations, String field, String message) {
        assertThat(violations)
                .extracting(
                        violation -> violation.getPropertyPath().toString(),
                        ConstraintViolation::getMessage
                )
                .contains(tuple(field, message));
    }
}
