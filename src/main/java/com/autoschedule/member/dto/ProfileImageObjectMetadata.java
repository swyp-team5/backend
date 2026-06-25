package com.autoschedule.member.dto;

/**
 * S3에 업로드된 프로필 이미지 객체의 검증용 메타데이터를 표현한다.
 */
public record ProfileImageObjectMetadata(
        String contentType,
        long fileSize,
        byte[] firstBytes
) {
}
