package com.autoschedule.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.autoschedule.member.config.ProfileImageProperties;
import com.autoschedule.member.infra.S3ProfileImageStorage;
import com.autoschedule.member.service.ProfileImageUploadTarget;
import com.autoschedule.member.service.ProfileImageUploadUrl;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * S3 프로필 이미지 저장소의 presigned URL 생성 규칙을 검증한다.
 */
class S3ProfileImageStorageTest {

    /**
     * presigned PUT URL은 모바일 클라이언트가 직접 맞추기 어려운 Content-Length를 서명 대상에 포함하지 않는다.
     */
    @Test
    void createUploadUrlDoesNotRequireSignedContentLengthHeader() {
        try (S3Presigner presigner = S3Presigner.builder()
                .region(Region.AP_NORTHEAST_2)
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create("test-access-key", "test-secret-key")
                ))
                .build()) {
            S3ProfileImageStorage storage = new S3ProfileImageStorage(
                    new ProfileImageProperties(
                            "ap-northeast-2",
                            "autoschedule-profile-test",
                            "https://cdn.example.com",
                            "profile-images",
                            300,
                            10_485_760
                    ),
                    mock(S3Client.class),
                    presigner
            );

            ProfileImageUploadUrl uploadUrl = storage.createUploadUrl(new ProfileImageUploadTarget(
                    "profile-images/1/profile.png",
                    "profile.png",
                    "image/png",
                    1024
            ));

            assertThat(uploadUrl.headers().keySet())
                    .noneMatch(headerName -> "content-length".equalsIgnoreCase(headerName));
            assertThat(uploadUrl.uploadUrl()).doesNotContain("content-length");
        }
    }
}
