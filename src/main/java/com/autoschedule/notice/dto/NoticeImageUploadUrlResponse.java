package com.autoschedule.notice.dto;

import java.util.Map;

/**
 * 공지 이미지 S3 직접 업로드에 필요한 presigned URL 응답을 표현한다.
 */
public record NoticeImageUploadUrlResponse(
        String uploadUrl,
        String objectKey,
        String storedFileName,
        Map<String, String> headers,
        long expiresInSeconds
) {

    /**
     * 저장소 포트 응답 값을 모바일 클라이언트 응답 DTO로 변환한다.
     */
    public static NoticeImageUploadUrlResponse from(NoticeImageUploadUrl uploadUrl) {
        return new NoticeImageUploadUrlResponse(
                uploadUrl.uploadUrl(),
                uploadUrl.objectKey(),
                uploadUrl.storedFileName(),
                uploadUrl.headers(),
                uploadUrl.expiresInSeconds()
        );
    }
}
