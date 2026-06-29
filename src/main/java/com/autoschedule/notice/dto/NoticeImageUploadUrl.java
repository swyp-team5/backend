package com.autoschedule.notice.dto;

import java.util.Map;

/**
 * 공지 이미지 S3 직접 업로드에 필요한 presigned URL 정보를 표현한다.
 */
public record NoticeImageUploadUrl(
        String uploadUrl,
        String objectKey,
        String storedFileName,
        Map<String, String> headers,
        long expiresInSeconds
) {
}
