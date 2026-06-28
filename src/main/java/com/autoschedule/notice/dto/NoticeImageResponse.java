package com.autoschedule.notice.dto;

import com.autoschedule.notice.domain.NoticeImage;

/**
 * 공지에 연결된 이미지 메타데이터 응답을 표현한다.
 */
public record NoticeImageResponse(
        Long noticeImageId,
        String originalFileName,
        String storedFileName,
        String objectKey,
        String imageUrl,
        String contentType,
        long fileSize,
        int displayOrder
) {

    /**
     * 공지 이미지 엔티티를 모바일 응답 DTO로 변환한다.
     */
    public static NoticeImageResponse from(NoticeImage image) {
        return new NoticeImageResponse(
                image.getId(),
                image.getOriginalFileName(),
                image.getStoredFileName(),
                image.getObjectKey(),
                image.getImageUrl(),
                image.getContentType(),
                image.getFileSize(),
                image.getDisplayOrder()
        );
    }
}
