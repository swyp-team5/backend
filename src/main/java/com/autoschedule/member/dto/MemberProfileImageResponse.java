package com.autoschedule.member.dto;

import com.autoschedule.member.domain.ProfileImage;
import java.time.LocalDateTime;

/**
 * 회원 프로필 이미지 응답 정보를 표현한다.
 */
public record MemberProfileImageResponse(
        Long profileImageId,
        String originalFileName,
        String storedFileName,
        String objectKey,
        String imageUrl,
        String contentType,
        long fileSize,
        LocalDateTime uploadedAt
) {

    /**
     * 프로필 이미지 엔티티를 모바일 클라이언트 응답 DTO로 변환한다.
     */
    public static MemberProfileImageResponse from(ProfileImage profileImage) {
        return new MemberProfileImageResponse(
                profileImage.getId(),
                profileImage.getOriginalFileName(),
                profileImage.getStoredFileName(),
                profileImage.getObjectKey(),
                profileImage.getImageUrl(),
                profileImage.getContentType(),
                profileImage.getFileSize(),
                profileImage.getUploadedAt()
        );
    }
}
