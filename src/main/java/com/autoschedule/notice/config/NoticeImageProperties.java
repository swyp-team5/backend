package com.autoschedule.notice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 공지 이미지 S3 업로드와 검증에 필요한 환경 설정 값을 관리한다.
 */
@ConfigurationProperties(prefix = "notice.image")
public record NoticeImageProperties(
        String region,
        String bucket,
        String publicBaseUrl,
        String objectKeyPrefix,
        long uploadUrlExpiresSeconds,
        long maxSizeBytes,
        int maxCount
) {
}
