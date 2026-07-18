package com.autoschedule.notice.dto;

/**
 * 공지 이미지 S3 업로드 URL 생성에 필요한 저장 대상 정보를 표현한다.
 */
public record NoticeImageUploadTarget(
        String objectKey,
        String storedFileName,
        String contentType,
        long fileSize
) {
}
