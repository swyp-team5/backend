package com.autoschedule.member.service;

import com.autoschedule.global.exception.ApiException;
import com.autoschedule.global.exception.ErrorCode;
import com.autoschedule.member.config.ProfileImageProperties;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 프로필 이미지 업로드 메타데이터와 실제 이미지 파일 여부를 검증한다.
 */
@Component
public class ProfileImageValidator {

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("image/jpeg", "image/png", "image/webp");
    private static final Map<String, Set<String>> ALLOWED_EXTENSIONS = Map.of(
            "image/jpeg", Set.of("jpg", "jpeg"),
            "image/png", Set.of("png"),
            "image/webp", Set.of("webp")
    );

    private final ProfileImageProperties properties;

    public ProfileImageValidator(ProfileImageProperties properties) {
        this.properties = properties;
    }

    /**
     * 업로드 URL 발급 전에 파일명, content type, 파일 크기 메타데이터를 검증한다.
     */
    public void validateUploadMetadata(String originalFileName, String contentType, long fileSize) {
        String normalizedContentType = normalizeContentType(contentType);
        validateContentType(normalizedContentType);
        validateFileSize(fileSize);
        validateFileName(originalFileName, normalizedContentType);
    }

    /**
     * S3 업로드 완료 후 객체 크기, content type, 매직 바이트를 검증한다.
     */
    public void validateActualImage(String contentType, long fileSize, byte[] firstBytes) {
        String normalizedContentType = normalizeContentType(contentType);
        validateContentType(normalizedContentType);
        validateFileSize(fileSize);
        if (!matchesMagicBytes(normalizedContentType, firstBytes)) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "업로드된 파일이 올바른 이미지 파일이 아닙니다.");
        }
    }

    /**
     * content type 표기를 소문자 기준으로 정규화한다.
     */
    public String normalizeContentType(String contentType) {
        if (!StringUtils.hasText(contentType)) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "프로필 이미지 content type은 필수입니다.");
        }
        return contentType.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * 원본 파일명에서 확장자를 추출한다.
     */
    public String extractExtension(String originalFileName) {
        if (!StringUtils.hasText(originalFileName)) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "프로필 이미지 원본 파일명은 필수입니다.");
        }
        if (containsPathOrControlCharacter(originalFileName)) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "프로필 이미지 원본 파일명에는 경로 문자나 제어 문자를 포함할 수 없습니다.");
        }
        int dotIndex = originalFileName.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == originalFileName.length() - 1) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "프로필 이미지 파일 확장자가 올바르지 않습니다.");
        }
        return originalFileName.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
    }

    /**
     * 지원하는 이미지 content type인지 확인한다.
     */
    private void validateContentType(String contentType) {
        if (!ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "프로필 이미지는 JPG, PNG, WEBP 형식만 업로드할 수 있습니다.");
        }
    }

    /**
     * 파일 크기가 1 byte 이상 10MB 이하인지 확인한다.
     */
    private void validateFileSize(long fileSize) {
        if (fileSize <= 0) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "프로필 이미지 파일 크기는 1 byte 이상이어야 합니다.");
        }
        if (fileSize > properties.maxSizeBytes()) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "프로필 이미지 파일 크기는 최대 10MB까지 허용됩니다.");
        }
    }

    /**
     * 파일 확장자와 content type의 조합이 일치하는지 확인한다.
     */
    private void validateFileName(String originalFileName, String contentType) {
        String extension = extractExtension(originalFileName);
        if (!ALLOWED_EXTENSIONS.getOrDefault(contentType, Set.of()).contains(extension)) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "프로필 이미지 파일 확장자와 형식이 일치하지 않습니다.");
        }
    }

    /**
     * 원본 파일명에 경로 문자나 제어 문자가 포함되어 있는지 확인한다.
     */
    private boolean containsPathOrControlCharacter(String originalFileName) {
        return originalFileName.chars()
                .anyMatch(value -> value == '/' || value == '\\' || Character.isISOControl(value));
    }

    /**
     * content type에 맞는 이미지 매직 바이트를 갖는지 확인한다.
     */
    private boolean matchesMagicBytes(String contentType, byte[] firstBytes) {
        if (firstBytes == null) {
            return false;
        }
        return switch (contentType) {
            case "image/jpeg" -> firstBytes.length >= 3
                    && firstBytes[0] == (byte) 0xFF
                    && firstBytes[1] == (byte) 0xD8
                    && firstBytes[2] == (byte) 0xFF;
            case "image/png" -> firstBytes.length >= 4
                    && firstBytes[0] == (byte) 0x89
                    && firstBytes[1] == 0x50
                    && firstBytes[2] == 0x4E
                    && firstBytes[3] == 0x47;
            case "image/webp" -> firstBytes.length >= 12
                    && firstBytes[0] == 'R'
                    && firstBytes[1] == 'I'
                    && firstBytes[2] == 'F'
                    && firstBytes[3] == 'F'
                    && firstBytes[8] == 'W'
                    && firstBytes[9] == 'E'
                    && firstBytes[10] == 'B'
                    && firstBytes[11] == 'P';
            default -> false;
        };
    }
}
