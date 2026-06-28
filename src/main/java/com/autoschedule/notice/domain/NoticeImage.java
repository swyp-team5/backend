package com.autoschedule.notice.domain;

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
 * 공지에 첨부되는 S3 이미지 파일의 업로드 시도와 확정 메타데이터를 관리한다.
 */
@Getter
@Entity
@Table(name = "notice_image")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NoticeImage extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "notice_image_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "notice_id")
    private Notice notice;

    @Column(name = "uploader_member_id", nullable = false)
    private Long uploaderMemberId;

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

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NoticeImageStatus status;

    @Column(name = "uploaded_at")
    private LocalDateTime uploadedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    /**
     * S3 업로드 URL이 발급된 대기 상태의 공지 이미지 메타데이터를 생성한다.
     */
    public static NoticeImage createPending(
            Long uploaderMemberId,
            String originalFileName,
            String storedFileName,
            String objectKey,
            String imageUrl,
            String contentType,
            long fileSize
    ) {
        NoticeImage image = new NoticeImage();
        image.uploaderMemberId = uploaderMemberId;
        image.originalFileName = originalFileName;
        image.storedFileName = storedFileName;
        image.objectKey = objectKey;
        image.imageUrl = imageUrl;
        image.contentType = contentType;
        image.fileSize = fileSize;
        image.displayOrder = 0;
        image.status = NoticeImageStatus.PENDING;
        return image;
    }

    /**
     * 검증 완료된 S3 객체를 공지에 첨부된 활성 이미지로 확정한다.
     */
    public void activate(Notice notice, String contentType, long fileSize, int displayOrder, LocalDateTime uploadedAt) {
        this.notice = notice;
        this.contentType = contentType;
        this.fileSize = fileSize;
        this.displayOrder = displayOrder;
        this.status = NoticeImageStatus.ACTIVE;
        this.uploadedAt = uploadedAt;
        this.deletedAt = null;
    }

    /**
     * 이미지 row를 삭제 상태로 전환한다.
     */
    public void markDeleted(LocalDateTime deletedAt) {
        this.status = NoticeImageStatus.DELETED;
        this.deletedAt = deletedAt;
    }
}
