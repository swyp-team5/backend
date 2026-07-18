package com.autoschedule.member.infra;

import com.autoschedule.global.exception.ApiException;
import com.autoschedule.global.exception.ErrorCode;
import com.autoschedule.member.config.ProfileImageProperties;
import com.autoschedule.member.dto.ProfileImageObjectMetadata;
import com.autoschedule.member.repository.ProfileImageStorage;
import com.autoschedule.member.dto.ProfileImageUploadTarget;
import com.autoschedule.member.dto.ProfileImageUploadUrl;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;

/**
 * AWS S3와 통신하여 프로필 이미지 presigned URL 발급과 객체 검증, 삭제를 담당한다.
 */
@Component
@RequiredArgsConstructor
public class S3ProfileImageStorage implements ProfileImageStorage {

    private static final String MAGIC_BYTE_RANGE = "bytes=0-63";

    private final ProfileImageProperties properties;
    @Qualifier("profileImageS3Client")
    private final S3Client profileImageS3Client;
    @Qualifier("profileImageS3Presigner")
    private final S3Presigner profileImageS3Presigner;

    /**
     * 클라이언트가 S3로 직접 PUT 업로드할 수 있는 presigned URL을 생성한다.
     */
    @Override
    public ProfileImageUploadUrl createUploadUrl(ProfileImageUploadTarget target) {
        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(properties.bucket())
                .key(target.objectKey())
                .contentType(target.contentType())
                .build();

        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(Duration.ofSeconds(properties.uploadUrlExpiresSeconds()))
                .putObjectRequest(putObjectRequest)
                .build();

        PresignedPutObjectRequest presignedRequest = profileImageS3Presigner.presignPutObject(presignRequest);
        Map<String, String> signedHeaders = presignedRequest.signedHeaders()
                .entrySet()
                .stream()
                .filter(entry -> !"host".equalsIgnoreCase(entry.getKey()))
                .collect(Collectors.toUnmodifiableMap(
                        Map.Entry::getKey,
                        entry -> String.join(",", entry.getValue())
                ));
        if (signedHeaders.keySet().stream().noneMatch("content-type"::equalsIgnoreCase)) {
            Map<String, String> headersWithContentType = new LinkedHashMap<>(signedHeaders);
            headersWithContentType.put("Content-Type", target.contentType());
            signedHeaders = Map.copyOf(headersWithContentType);
        }

        return new ProfileImageUploadUrl(
                presignedRequest.url().toString(),
                target.objectKey(),
                target.storedFileName(),
                signedHeaders,
                properties.uploadUrlExpiresSeconds()
        );
    }

    /**
     * S3 객체의 HeadObject 정보와 매직 바이트 검증용 앞부분 바이트를 조회한다.
     */
    @Override
    public ProfileImageObjectMetadata getObjectMetadata(String objectKey) {
        try {
            HeadObjectResponse headObject = profileImageS3Client.headObject(HeadObjectRequest.builder()
                    .bucket(properties.bucket())
                    .key(objectKey)
                    .build());
            ResponseBytes<GetObjectResponse> firstBytes = profileImageS3Client.getObjectAsBytes(GetObjectRequest.builder()
                    .bucket(properties.bucket())
                    .key(objectKey)
                    .range(MAGIC_BYTE_RANGE)
                    .build());
            return new ProfileImageObjectMetadata(
                    headObject.contentType(),
                    headObject.contentLength(),
                    firstBytes.asByteArray()
            );
        } catch (NoSuchKeyException exception) {
            throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "업로드된 프로필 이미지 파일을 찾을 수 없습니다.");
        }
    }

    /**
     * S3 객체를 삭제한다.
     */
    @Override
    public void deleteObject(String objectKey) {
        profileImageS3Client.deleteObject(DeleteObjectRequest.builder()
                .bucket(properties.bucket())
                .key(objectKey)
                .build());
    }
}
