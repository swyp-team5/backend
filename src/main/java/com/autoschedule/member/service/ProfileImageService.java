package com.autoschedule.member.service;

import com.autoschedule.global.exception.ApiException;
import com.autoschedule.global.exception.ErrorCode;
import com.autoschedule.member.config.ProfileImageProperties;
import com.autoschedule.member.domain.Member;
import com.autoschedule.member.domain.MemberStatus;
import com.autoschedule.member.domain.ProfileImage;
import com.autoschedule.member.domain.ProfileImageStatus;
import com.autoschedule.member.dto.*;
import com.autoschedule.member.repository.MemberRepository;
import com.autoschedule.member.repository.ProfileImageRepository;
import java.time.LocalDateTime;
import java.util.UUID;

import com.autoschedule.member.repository.ProfileImageStorage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

/**
 * 프로필 이미지 업로드 URL 발급, 업로드 확정, 삭제 비즈니스 규칙을 처리한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProfileImageService {

    private final MemberRepository memberRepository;
    private final ProfileImageRepository profileImageRepository;
    private final ProfileImageStorage profileImageStorage;
    private final ProfileImageValidator profileImageValidator;
    private final ProfileImageProperties profileImageProperties;

    /**
     * 현재 로그인한 회원이 S3로 직접 업로드할 수 있는 presigned URL을 발급하고 PENDING row를 생성한다.
     */
    @Transactional
    public ProfileImageUploadUrlResponse createUploadUrl(Long memberId, ProfileImageUploadUrlRequest request) {
        Member member = findActiveMemberForUpdate(memberId);
        profileImageValidator.validateUploadMetadata(request.originalFileName(), request.contentType(), request.fileSize());

        deleteExistingPendingImage(member.getId());

        String contentType = profileImageValidator.normalizeContentType(request.contentType());
        String extension = profileImageValidator.extractExtension(request.originalFileName());
        String storedFileName = UUID.randomUUID() + "." + extension;
        String objectKey = profileImageProperties.objectKeyPrefix() + "/" + member.getId() + "/" + storedFileName;
        String imageUrl = buildImageUrl(objectKey);

        ProfileImage pendingImage = profileImageRepository.save(ProfileImage.createPending(
                member,
                request.originalFileName(),
                storedFileName,
                objectKey,
                imageUrl,
                contentType,
                request.fileSize()
        ));

        ProfileImageUploadUrl uploadUrl = profileImageStorage.createUploadUrl(new ProfileImageUploadTarget(
                pendingImage.getObjectKey(),
                pendingImage.getStoredFileName(),
                pendingImage.getContentType(),
                pendingImage.getFileSize()
        ));
        return ProfileImageUploadUrlResponse.from(uploadUrl);
    }

    /**
     * S3 업로드 완료 객체를 검증하고 PENDING row를 현재 회원의 ACTIVE 프로필 이미지로 승격한다.
     */
    @Transactional(noRollbackFor = ApiException.class)
    public MemberProfileResponse confirmUploadedImage(Long memberId, ProfileImageConfirmRequest request) {
        Member member = findActiveMemberForUpdate(memberId);
        validateOwnedObjectKey(member.getId(), request.objectKey());

        ProfileImage pendingImage = findPendingImage(member.getId(), request.objectKey());
        validatePendingImageNotExpired(pendingImage);

        ProfileImageObjectMetadata metadata;
        try {
            metadata = profileImageStorage.getObjectMetadata(request.objectKey());
            profileImageValidator.validateActualImage(metadata.contentType(), metadata.fileSize(), metadata.firstBytes());
        } catch (ApiException exception) {
            pendingImage.markDeleted(LocalDateTime.now());
            deleteObjectAfterCommit(request.objectKey());
            throw exception;
        }

        profileImageRepository.findByMember_IdAndStatusAndDeletedAtIsNull(member.getId(), ProfileImageStatus.ACTIVE)
                .ifPresent(activeImage -> {
                    activeImage.markDeleted(LocalDateTime.now());
                    profileImageRepository.flush();
                    deleteObjectAfterCommit(activeImage.getObjectKey());
                });

        pendingImage.activate(
                profileImageValidator.normalizeContentType(metadata.contentType()),
                metadata.fileSize(),
                LocalDateTime.now()
        );

        return MemberProfileResponse.from(member, pendingImage);
    }

    /**
     * 현재 회원의 프로필 이미지를 삭제 상태로 전환하고 S3 객체 삭제를 커밋 이후 요청한다.
     */
    @Transactional
    public void deleteProfileImage(Long memberId) {
        findActiveMemberForUpdate(memberId);
        profileImageRepository.findByMember_IdAndStatusAndDeletedAtIsNull(memberId, ProfileImageStatus.ACTIVE)
                .ifPresent(profileImage -> {
                    profileImage.markDeleted(LocalDateTime.now());
                    deleteObjectAfterCommit(profileImage.getObjectKey());
                });
    }

    /**
     * 인증된 회원 ID로 활성 회원을 조회하면서 쓰기 락을 건다.
     */
    private Member findActiveMemberForUpdate(Long memberId) {
        return memberRepository.findByIdAndStatusForUpdate(memberId, MemberStatus.ACTIVE)
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED, "회원 정보를 찾을 수 없습니다."));
    }

    /**
     * 기존 PENDING 이미지가 있으면 삭제 상태로 전환하고 S3 객체 삭제를 커밋 이후 요청한다.
     */
    private void deleteExistingPendingImage(Long memberId) {
        profileImageRepository.findByMember_IdAndStatusAndDeletedAtIsNull(memberId, ProfileImageStatus.PENDING)
                .ifPresent(pendingImage -> {
                    pendingImage.markDeleted(LocalDateTime.now());
                    profileImageRepository.flush();
                    deleteObjectAfterCommit(pendingImage.getObjectKey());
                });
    }

    /**
     * 회원 ID와 object key로 현재 유효한 PENDING 이미지를 조회한다.
     */
    private ProfileImage findPendingImage(Long memberId, String objectKey) {
        return profileImageRepository
                .findByMember_IdAndObjectKeyAndStatusAndDeletedAtIsNull(
                        memberId,
                        objectKey,
                        ProfileImageStatus.PENDING
                )
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "확정 가능한 프로필 이미지 업로드 이력을 찾을 수 없습니다."));
    }

    /**
     * presigned URL 만료 시간이 지난 PENDING 이미지는 확정하지 않는다.
     */
    private void validatePendingImageNotExpired(ProfileImage pendingImage) {
        LocalDateTime createdAt = pendingImage.getCreatedAt();
        if (createdAt == null) {
            return;
        }

        LocalDateTime expiresAt = createdAt.plusSeconds(profileImageProperties.uploadUrlExpiresSeconds());
        if (expiresAt.isBefore(LocalDateTime.now())) {
            pendingImage.markDeleted(LocalDateTime.now());
            deleteObjectAfterCommit(pendingImage.getObjectKey());
            throw new ApiException(ErrorCode.INVALID_REQUEST, "프로필 이미지 업로드 URL이 만료되었습니다. 다시 업로드해주세요.");
        }
    }

    /**
     * 요청한 S3 object key가 현재 회원의 프로필 이미지 경로인지 확인한다.
     */
    private void validateOwnedObjectKey(Long memberId, String objectKey) {
        String expectedPrefix = profileImageProperties.objectKeyPrefix() + "/" + memberId + "/";
        if (!StringUtils.hasText(objectKey) || !objectKey.startsWith(expectedPrefix)) {
            throw new ApiException(ErrorCode.FORBIDDEN, "본인의 프로필 이미지 업로드 경로만 확정할 수 있습니다.");
        }
    }

    /**
     * 모바일 앱에서 표시할 프로필 이미지 URL을 생성한다.
     */
    private String buildImageUrl(String objectKey) {
        String baseUrl = profileImageProperties.publicBaseUrl();
        if (!StringUtils.hasText(baseUrl)) {
            return objectKey;
        }
        return baseUrl.replaceAll("/+$", "") + "/" + objectKey;
    }

    /**
     * DB 트랜잭션 커밋 이후 S3 객체 삭제를 best-effort로 수행한다.
     */
    private void deleteObjectAfterCommit(String objectKey) {
        if (!StringUtils.hasText(objectKey)) {
            return;
        }

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {

                /**
                 * DB 변경이 확정된 뒤 S3 객체 삭제를 시도한다.
                 */
                @Override
                public void afterCommit() {
                    deleteObjectQuietly(objectKey);
                }
            });
            return;
        }

        deleteObjectQuietly(objectKey);
    }

    /**
     * S3 삭제 실패가 API 성공/실패 결과를 바꾸지 않도록 로그만 남긴다.
     */
    private void deleteObjectQuietly(String objectKey) {
        try {
            profileImageStorage.deleteObject(objectKey);
        } catch (RuntimeException exception) {
            log.error("Failed to delete profile image object from S3. objectKey={}", objectKey, exception);
        }
    }
}
