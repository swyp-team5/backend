package com.autoschedule.member.dto;

import java.util.Map;

/**
 * 클라이언트가 S3로 직접 업로드할 때 필요한 presigned URL 정보를 표현한다.
 */
public record ProfileImageUploadUrl(
        String uploadUrl,
        String objectKey,
        String storedFileName,
        Map<String, String> headers,
        long expiresInSeconds
) {
}
