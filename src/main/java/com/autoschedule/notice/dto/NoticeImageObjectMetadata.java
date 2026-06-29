package com.autoschedule.notice.dto;

/**
 * S3에 업로드된 공지 이미지 객체의 실제 메타데이터와 검증용 바이트를 표현한다.
 */
public record NoticeImageObjectMetadata(
        String contentType,
        long fileSize,
        byte[] firstBytes
) {
}
