package com.autoschedule.notice.repository;

import com.autoschedule.notice.dto.NoticeImageObjectMetadata;
import com.autoschedule.notice.dto.NoticeImageUploadTarget;
import com.autoschedule.notice.dto.NoticeImageUploadUrl;

/**
 * 공지 이미지 실제 저장소와 통신하는 책임을 추상화한다.
 */
public interface NoticeImageStorage {

    /**
     * 클라이언트가 S3로 직접 업로드할 presigned URL을 생성한다.
     */
    NoticeImageUploadUrl createUploadUrl(NoticeImageUploadTarget target);

    /**
     * 업로드된 S3 객체의 실제 메타데이터와 이미지 검증용 앞부분 바이트를 조회한다.
     */
    NoticeImageObjectMetadata getObjectMetadata(String objectKey);

    /**
     * S3 객체를 삭제한다.
     */
    void deleteObject(String objectKey);
}
