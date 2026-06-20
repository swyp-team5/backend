package com.autoschedule.member.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 프로필 이미지 S3 업로드와 검증에 필요한 환경 설정값을 관리한다.
 */
@ConfigurationProperties(prefix = "profile.image")
public record ProfileImageProperties(
        String region,
        String bucket,
        String publicBaseUrl,
        String objectKeyPrefix,
        long uploadUrlExpiresSeconds,
        long maxSizeBytes
) {
}
