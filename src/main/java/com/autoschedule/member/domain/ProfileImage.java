package com.autoschedule.member.domain;

import com.autoschedule.global.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 회원의 프로필 이미지 업로드 시도와 현재 사용 중인 이미지 메타데이터를 관리한다.
 */
@Getter
@Entity
@Table(name = "profile_image")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProfileImage extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "profile_image_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Column(name = "original_file_name", nullable = false, length = 255)
    private String originalFileName;

    @Column(name = "stored_file_name", nullable = false, length = 100)
    private String storedFileName;

    @Column(name = "object_key", nullable = false, length = 500)
    private String objectKey;

    @Column(name = "image_url", nullable = false, length = 700)
    private String imageUrl;

    @Column(name = "content_type", nullable = false, length = 50)
    private String contentType;

    @Column(name = "file_size", nullable = false)
    private long fileSize;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProfileImageStatus status;

    @Column(name = "uploaded_at")
    private LocalDateTime uploadedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    /**
     * S3 업로드 URL이 발급된 대기 상태의 프로필 이미지 메타데이터를 생성한다.
     */
    public static ProfileImage createPending(
            Member member,
            String originalFileName,
            String storedFileName,
            String objectKey,
            String imageUrl,
            String contentType,
            long fileSize
    ) {
        ProfileImage profileImage = new ProfileImage();
        profileImage.member = member;
        profileImage.originalFileName = originalFileName;
        profileImage.storedFileName = storedFileName;
        profileImage.objectKey = objectKey;
        profileImage.imageUrl = imageUrl;
        profileImage.contentType = contentType;
        profileImage.fileSize = fileSize;
        profileImage.status = ProfileImageStatus.PENDING;
        return profileImage;
    }

    /**
     * 업로드 대기 상태의 이미지를 현재 사용 중인 프로필 이미지로 확정한다.
     */
    public void activate(String contentType, long fileSize, LocalDateTime uploadedAt) {
        this.contentType = contentType;
        this.fileSize = fileSize;
        this.status = ProfileImageStatus.ACTIVE;
        this.uploadedAt = uploadedAt;
        this.deletedAt = null;
    }

    /**
     * 현재 row를 삭제 상태로 전환한다.
     */
    public void markDeleted(LocalDateTime deletedAt) {
        this.status = ProfileImageStatus.DELETED;
        this.deletedAt = deletedAt;
    }
}
