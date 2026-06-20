package com.autoschedule.member.dto;

import com.autoschedule.member.service.ProfileImageUploadUrl;
import java.util.Map;

/**
 * 프로필 이미지 S3 직접 업로드에 필요한 presigned URL 응답을 표현한다.
 */
public record ProfileImageUploadUrlResponse(
        String uploadUrl,
        String objectKey,
        String storedFileName,
        Map<String, String> headers,
        long expiresInSeconds
) {

    /**
     * 저장소 포트 응답 값을 모바일 클라이언트 응답 DTO로 변환한다.
     */
    public static ProfileImageUploadUrlResponse from(ProfileImageUploadUrl uploadUrl) {
        return new ProfileImageUploadUrlResponse(
                uploadUrl.uploadUrl(),
                uploadUrl.objectKey(),
                uploadUrl.storedFileName(),
                uploadUrl.headers(),
                uploadUrl.expiresInSeconds()
        );
    }
}
