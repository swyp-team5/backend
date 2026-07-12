package com.autoschedule.notice.service;

import com.autoschedule.global.exception.ApiException;
import com.autoschedule.global.exception.ErrorCode;
import com.autoschedule.member.domain.Member;
import com.autoschedule.member.repository.MemberRepository;
import com.autoschedule.notice.config.NoticeImageProperties;
import com.autoschedule.notice.domain.Notice;
import com.autoschedule.notice.domain.NoticeImage;
import com.autoschedule.notice.domain.NoticeImageStatus;
import com.autoschedule.notice.dto.NoticeImageObjectMetadata;
import com.autoschedule.notice.dto.NoticeImageResponse;
import com.autoschedule.notice.dto.NoticeImageUploadTarget;
import com.autoschedule.notice.dto.NoticeImageUploadUrl;
import com.autoschedule.notice.dto.NoticeImageUploadUrlRequest;
import com.autoschedule.notice.dto.NoticeImageUploadUrlResponse;
import com.autoschedule.notice.repository.NoticeImageRepository;
import com.autoschedule.notice.repository.NoticeImageStorage;
import com.autoschedule.workplace.domain.WorkPlace;
import com.autoschedule.workplace.repository.WorkPlaceRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

/**
 * 공지 이미지 업로드 URL 발급, S3 객체 검증, 공지 연결, 삭제를 처리한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NoticeImageService {

    private final MemberRepository memberRepository;
    private final WorkPlaceRepository workPlaceRepository;
    private final NoticeImageRepository noticeImageRepository;
    private final NoticeImageStorage noticeImageStorage;
    private final NoticeImageValidator noticeImageValidator;
    private final NoticeImageProperties noticeImageProperties;

    /**
     * 사장이 공지 이미지 파일을 S3로 직접 업로드할 수 있는 presigned URL을 발급한다.
     */
    @Transactional
    public NoticeImageUploadUrlResponse createUploadUrl(
            Long ownerMemberId,
            Long workPlaceId,
            NoticeImageUploadUrlRequest request
    ) {
        Member owner = findActiveMember(ownerMemberId);
        WorkPlace workPlace = findOwnedActiveWorkPlace(workPlaceId, owner.getId());
        noticeImageValidator.validateUploadMetadata(request.originalFileName(), request.contentType(), request.fileSize());

        String contentType = noticeImageValidator.normalizeContentType(request.contentType());
        String extension = noticeImageValidator.extractExtension(request.originalFileName());
        String storedFileName = UUID.randomUUID() + "." + extension;
        String objectKey = "%s/%d/%d/%s".formatted(
                noticeImageProperties.objectKeyPrefix(),
                workPlace.getId(),
                owner.getId(),
                storedFileName
        );
        String imageUrl = buildImageUrl(objectKey);

        NoticeImage pendingImage = noticeImageRepository.save(NoticeImage.createPending(
                owner.getId(),
                request.originalFileName(),
                storedFileName,
                objectKey,
                imageUrl,
                contentType,
                request.fileSize()
        ));

        NoticeImageUploadUrl uploadUrl = noticeImageStorage.createUploadUrl(new NoticeImageUploadTarget(
                pendingImage.getObjectKey(),
                pendingImage.getStoredFileName(),
                pendingImage.getContentType(),
                pendingImage.getFileSize()
        ));
        return NoticeImageUploadUrlResponse.from(uploadUrl);
    }

    /**
     * 공지 작성 요청에 포함된 object key 목록을 검증하고 S3 실제 이미지 정보를 미리 확인한다.
     */
    public List<PreparedNoticeImage> prepareImagesForNotice(
            WorkPlace workPlace,
            Long ownerMemberId,
            List<String> objectKeys
    ) {
        if (objectKeys == null || objectKeys.isEmpty()) {
            return List.of();
        }
        noticeImageValidator.validateImageCount(objectKeys.size());
        validateNoDuplicateObjectKeys(objectKeys);

        List<PreparedNoticeImage> preparedImages = new ArrayList<>();
        for (String objectKey : objectKeys) {
            validateOwnedObjectKey(workPlace.getId(), ownerMemberId, objectKey);
            NoticeImage pendingImage = findPendingImage(ownerMemberId, objectKey);
            validatePendingImageNotExpired(pendingImage);

            try {
                NoticeImageObjectMetadata metadata = noticeImageStorage.getObjectMetadata(objectKey);
                noticeImageValidator.validateActualImage(metadata.contentType(), metadata.fileSize(), metadata.firstBytes());
                preparedImages.add(new PreparedNoticeImage(pendingImage, metadata));
            } catch (ApiException exception) {
                pendingImage.markDeleted(LocalDateTime.now());
                deleteObjectAfterCommit(objectKey);
                throw exception;
            }
        }
        return preparedImages;
    }

    /**
     * 검증된 공지 이미지들을 생성된 공지에 연결하고 ACTIVE 상태로 확정한다.
     */
    public List<NoticeImageResponse> activatePreparedImages(Notice notice, List<PreparedNoticeImage> preparedImages) {
        if (preparedImages == null || preparedImages.isEmpty()) {
            return List.of();
        }
        LocalDateTime now = LocalDateTime.now();
        List<NoticeImageResponse> responses = new ArrayList<>();
        for (int index = 0; index < preparedImages.size(); index++) {
            PreparedNoticeImage preparedImage = preparedImages.get(index);
            NoticeImage image = preparedImage.noticeImage();
            NoticeImageObjectMetadata metadata = preparedImage.metadata();
            image.activate(
                    notice,
                    noticeImageValidator.normalizeContentType(metadata.contentType()),
                    metadata.fileSize(),
                    index + 1,
                    now
            );
            responses.add(NoticeImageResponse.from(image));
        }
        return responses;
    }

    /**
     * 공지 ID별 활성 이미지를 한 번에 조회해 응답 DTO 목록으로 묶는다.
     */
    @Transactional(readOnly = true)
    public Map<Long, List<NoticeImageResponse>> findActiveImagesByNoticeIds(Collection<Long> noticeIds) {
        if (noticeIds == null || noticeIds.isEmpty()) {
            return Map.of();
        }
        return noticeImageRepository
                .findByNotice_IdInAndStatusAndDeletedAtIsNullOrderByDisplayOrderAscIdAsc(
                        noticeIds,
                        NoticeImageStatus.ACTIVE
                )
                .stream()
                .collect(Collectors.groupingBy(
                        image -> image.getNotice().getId(),
                        Collectors.mapping(NoticeImageResponse::from, Collectors.toList())
                ));
    }

    /**
     * 공지 수정 요청의 object key 목록을 수정 후 최종 이미지 목록으로 해석해 이미지 연결 상태를 갱신한다.
     */
    public List<NoticeImageResponse> replaceImagesForNotice(
            Notice notice,
            WorkPlace workPlace,
            Long ownerMemberId,
            List<String> objectKeys
    ) {
        if (objectKeys == null) {
            return findActiveImages(notice.getId());
        }

        noticeImageValidator.validateImageCount(objectKeys.size());
        validateNoDuplicateObjectKeys(objectKeys);

        List<NoticeImage> activeImages = noticeImageRepository
                .findByNotice_IdAndStatusAndDeletedAtIsNullOrderByDisplayOrderAscIdAsc(
                        notice.getId(),
                        NoticeImageStatus.ACTIVE
                );
        Map<String, NoticeImage> activeImagesByObjectKey = activeImages.stream()
                .collect(Collectors.toMap(NoticeImage::getObjectKey, Function.identity()));

        List<NoticeImageResponse> responses = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        for (int index = 0; index < objectKeys.size(); index++) {
            String objectKey = objectKeys.get(index);
            NoticeImage activeImage = activeImagesByObjectKey.remove(objectKey);
            int displayOrder = index + 1;
            if (activeImage != null) {
                activeImage.changeDisplayOrder(displayOrder);
                responses.add(NoticeImageResponse.from(activeImage));
                continue;
            }

            validateOwnedObjectKey(workPlace.getId(), ownerMemberId, objectKey);
            NoticeImage pendingImage = findPendingImage(ownerMemberId, objectKey);
            validatePendingImageNotExpired(pendingImage);

            try {
                NoticeImageObjectMetadata metadata = noticeImageStorage.getObjectMetadata(objectKey);
                noticeImageValidator.validateActualImage(metadata.contentType(), metadata.fileSize(), metadata.firstBytes());
                pendingImage.activate(
                        notice,
                        noticeImageValidator.normalizeContentType(metadata.contentType()),
                        metadata.fileSize(),
                        displayOrder,
                        now
                );
                responses.add(NoticeImageResponse.from(pendingImage));
            } catch (ApiException exception) {
                pendingImage.markDeleted(now);
                deleteObjectAfterCommit(objectKey);
                throw exception;
            }
        }

        for (NoticeImage removedImage : activeImagesByObjectKey.values()) {
            removedImage.markDeleted(now);
            deleteObjectAfterCommit(removedImage.getObjectKey());
        }
        return responses;
    }

    /**
     * 공지 1건에 연결된 활성 이미지 목록을 조회한다.
     */
    @Transactional(readOnly = true)
    public List<NoticeImageResponse> findActiveImages(Long noticeId) {
        return noticeImageRepository
                .findByNotice_IdAndStatusAndDeletedAtIsNullOrderByDisplayOrderAscIdAsc(
                        noticeId,
                        NoticeImageStatus.ACTIVE
                )
                .stream()
                .map(NoticeImageResponse::from)
                .toList();
    }

    /**
     * 공지 삭제 시 연결된 활성 이미지 row를 삭제 상태로 전환하고 S3 객체 삭제를 예약한다.
     */
    public void deleteActiveImages(Notice notice) {
        List<NoticeImage> images = noticeImageRepository
                .findByNotice_IdAndStatusAndDeletedAtIsNullOrderByDisplayOrderAscIdAsc(
                        notice.getId(),
                        NoticeImageStatus.ACTIVE
                );
        LocalDateTime now = LocalDateTime.now();
        for (NoticeImage image : images) {
            image.markDeleted(now);
            deleteObjectAfterCommit(image.getObjectKey());
        }
    }

    /**
     * 인증 회원 ID로 활성 회원을 조회한다.
     */
    private Member findActiveMember(Long memberId) {
        return memberRepository.findActiveById(memberId)
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED, "인증 정보를 찾을 수 없습니다."));
    }

    /**
     * 사장이 소유한 활성 사업장을 조회한다.
     */
    private WorkPlace findOwnedActiveWorkPlace(Long workPlaceId, Long ownerMemberId) {
        return workPlaceRepository.findOwnedActiveById(workPlaceId, ownerMemberId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "조회할 수 있는 사업장을 찾을 수 없습니다."));
    }

    /**
     * 같은 공지 작성 요청 안에 동일 object key가 중복되는지 확인한다.
     */
    private void validateNoDuplicateObjectKeys(List<String> objectKeys) {
        if (new LinkedHashSet<>(objectKeys).size() != objectKeys.size()) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "공지 이미지 object key는 중복될 수 없습니다.");
        }
    }

    /**
     * 요청 object key가 현재 사업장과 작성자 경로에 속하는지 확인한다.
     */
    private void validateOwnedObjectKey(Long workPlaceId, Long ownerMemberId, String objectKey) {
        String expectedPrefix = "%s/%d/%d/".formatted(
                noticeImageProperties.objectKeyPrefix(),
                workPlaceId,
                ownerMemberId
        );
        if (!StringUtils.hasText(objectKey) || !objectKey.startsWith(expectedPrefix)) {
            throw new ApiException(ErrorCode.FORBIDDEN, "본인 사업장의 공지 이미지 업로드 경로만 사용할 수 있습니다.");
        }
    }

    /**
     * object key로 현재 유효한 PENDING 공지 이미지 업로드 이력을 조회한다.
     */
    private NoticeImage findPendingImage(Long ownerMemberId, String objectKey) {
        return noticeImageRepository
                .findByUploaderMemberIdAndObjectKeyAndStatusAndDeletedAtIsNull(
                        ownerMemberId,
                        objectKey,
                        NoticeImageStatus.PENDING
                )
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "확정 가능한 공지 이미지 업로드 이력을 찾을 수 없습니다."));
    }

    /**
     * presigned URL 만료 시간이 지난 PENDING 이미지는 확정하지 않는다.
     */
    private void validatePendingImageNotExpired(NoticeImage pendingImage) {
        LocalDateTime createdAt = pendingImage.getCreatedAt();
        if (createdAt == null) {
            return;
        }

        LocalDateTime expiresAt = createdAt.plusSeconds(noticeImageProperties.uploadUrlExpiresSeconds());
        if (expiresAt.isBefore(LocalDateTime.now())) {
            pendingImage.markDeleted(LocalDateTime.now());
            deleteObjectAfterCommit(pendingImage.getObjectKey());
            throw new ApiException(ErrorCode.INVALID_REQUEST, "공지 이미지 업로드 URL이 만료되었습니다. 다시 업로드해주세요.");
        }
    }

    /**
     * 모바일 앱에서 표시할 공지 이미지 URL을 생성한다.
     */
    private String buildImageUrl(String objectKey) {
        String baseUrl = noticeImageProperties.publicBaseUrl();
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
                 * DB 변경이 확정된 후 S3 객체 삭제를 시도한다.
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
     * S3 삭제 실패가 API 성공 여부를 바꾸지 않도록 오류 로그만 남긴다.
     */
    private void deleteObjectQuietly(String objectKey) {
        try {
            noticeImageStorage.deleteObject(objectKey);
        } catch (RuntimeException exception) {
            log.error("Failed to delete notice image object from S3. objectKey={}", objectKey, exception);
        }
    }

    /**
     * 공지에 연결하기 전 검증된 이미지 row와 실제 S3 메타데이터를 함께 보관한다.
     */
    public record PreparedNoticeImage(
            NoticeImage noticeImage,
            NoticeImageObjectMetadata metadata
    ) {
    }
}
