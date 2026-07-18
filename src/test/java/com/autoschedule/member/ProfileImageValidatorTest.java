package com.autoschedule.member;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.autoschedule.global.exception.ApiException;
import com.autoschedule.member.config.ProfileImageProperties;
import com.autoschedule.member.service.ProfileImageValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 프로필 이미지 업로드 요청과 실제 S3 객체의 이미지 여부 검증 규칙을 검증한다.
 */
class ProfileImageValidatorTest {

    private ProfileImageValidator validator;

    /**
     * 테스트마다 10MB 제한과 허용 content type 정책을 가진 검증기를 준비한다.
     */
    @BeforeEach
    void setUp() {
        ProfileImageProperties properties = new ProfileImageProperties(
                "ap-northeast-2",
                "autoschedule-profile-dev",
                "https://cdn.example.com",
                "profile-images",
                300,
                10 * 1024 * 1024
        );
        validator = new ProfileImageValidator(properties);
    }

    /**
     * jpeg, png, webp content type과 매직 바이트는 이미지로 허용한다.
     */
    @Test
    void supportedImageMagicBytesAreAccepted() {
        assertThatCode(() -> validator.validateActualImage("image/jpeg", 1024, bytes(0xFF, 0xD8, 0xFF, 0xE0)))
                .doesNotThrowAnyException();
        assertThatCode(() -> validator.validateActualImage("image/png", 1024, bytes(0x89, 0x50, 0x4E, 0x47)))
                .doesNotThrowAnyException();
        assertThatCode(() -> validator.validateActualImage("image/webp", 1024, "RIFFxxxxWEBP".getBytes()))
                .doesNotThrowAnyException();
    }

    /**
     * 이미지가 아닌 매직 바이트가 업로드되면 요청을 거절한다.
     */
    @Test
    void invalidMagicBytesAreRejected() {
        assertThatThrownBy(() -> validator.validateActualImage("image/png", 1024, "not-image".getBytes()))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("이미지");
    }

    /**
     * 10MB를 초과한 파일 크기는 요청 메타데이터 단계에서 거절한다.
     */
    @Test
    void oversizedUploadMetadataIsRejected() {
        assertThatThrownBy(() -> validator.validateUploadMetadata("profile.png", "image/png", 10 * 1024 * 1024L + 1))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("10MB");
    }

    /**
     * 허용하지 않는 content type은 요청 메타데이터 단계에서 거절한다.
     */
    @Test
    void unsupportedContentTypeIsRejected() {
        assertThatThrownBy(() -> validator.validateUploadMetadata("profile.gif", "image/gif", 1024))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("이미지");
    }

    /**
     * 정수 매직 바이트를 byte 배열로 변환한다.
     */
    private byte[] bytes(int... values) {
        byte[] bytes = new byte[values.length];
        for (int i = 0; i < values.length; i++) {
            bytes[i] = (byte) values[i];
        }
        return bytes;
    }
}
