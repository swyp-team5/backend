package com.autoschedule.member.repository;

import com.autoschedule.member.dto.ProfileImageObjectMetadata;
import com.autoschedule.member.dto.ProfileImageUploadTarget;
import com.autoschedule.member.dto.ProfileImageUploadUrl;

/**
 * 프로필 이미지 실제 저장소와 통신하는 책임을 추상화한다.
 */
public interface ProfileImageStorage {

    /**
     * 클라이언트가 직접 업로드할 S3 presigned URL을 생성한다.
     */
    ProfileImageUploadUrl createUploadUrl(ProfileImageUploadTarget target);

    /**
     * S3 객체의 메타데이터와 이미지 판별용 앞부분 바이트를 조회한다.
     */
    ProfileImageObjectMetadata getObjectMetadata(String objectKey);

    /**
     * S3 객체를 삭제한다.
     */
    void deleteObject(String objectKey);
}
