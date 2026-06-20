package com.autoschedule.member.repository;

import com.autoschedule.member.domain.ProfileImage;
import com.autoschedule.member.domain.ProfileImageStatus;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 회원 프로필 이미지 메타데이터의 조회와 저장을 담당한다.
 */
public interface ProfileImageRepository extends JpaRepository<ProfileImage, Long> {

    /**
     * 회원 ID와 상태로 현재 유효한 프로필 이미지 row를 조회한다.
     */
    Optional<ProfileImage> findByMember_IdAndStatusAndDeletedAtIsNull(Long memberId, ProfileImageStatus status);

    /**
     * 회원 ID, S3 object key, 상태로 현재 유효한 프로필 이미지 row를 조회한다.
     */
    Optional<ProfileImage> findByMember_IdAndObjectKeyAndStatusAndDeletedAtIsNull(
            Long memberId,
            String objectKey,
            ProfileImageStatus status
    );
}
