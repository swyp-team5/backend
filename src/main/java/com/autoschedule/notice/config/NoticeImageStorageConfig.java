package com.autoschedule.notice.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * 공지 이미지 저장소로 사용할 AWS S3 클라이언트와 presigner를 구성한다.
 */
@Configuration
@EnableConfigurationProperties(NoticeImageProperties.class)
public class NoticeImageStorageConfig {

    /**
     * 공지 이미지 S3 객체 메타데이터 조회와 삭제에 사용할 클라이언트를 생성한다.
     */
    @Bean
    public S3Client noticeImageS3Client(NoticeImageProperties properties) {
        return S3Client.builder()
                .region(Region.of(properties.region()))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }

    /**
     * 공지 이미지 직접 업로드용 presigned URL을 생성할 presigner를 생성한다.
     */
    @Bean
    public S3Presigner noticeImageS3Presigner(NoticeImageProperties properties) {
        return S3Presigner.builder()
                .region(Region.of(properties.region()))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }
}
