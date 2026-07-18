package com.autoschedule.member.dto;

/**
 * S3 presigned upload URL을 발급할 대상 객체 정보를 표현한다.
 */
public record ProfileImageUploadTarget(
        String objectKey,
        String storedFileName,
        String contentType,
        long fileSize
) {
}
