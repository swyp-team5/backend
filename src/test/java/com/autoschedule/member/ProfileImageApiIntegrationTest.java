package com.autoschedule.member;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.autoschedule.auth.domain.TokenType;
import com.autoschedule.auth.jwt.JwtTokenProvider;
import com.autoschedule.member.domain.Member;
import com.autoschedule.member.domain.MemberRole;
import com.autoschedule.member.domain.SocialProvider;
import com.autoschedule.member.repository.MemberRepository;
import com.autoschedule.member.service.ProfileImageObjectMetadata;
import com.autoschedule.member.service.ProfileImageStorage;
import com.autoschedule.member.service.ProfileImageUploadTarget;
import com.autoschedule.member.service.ProfileImageUploadUrl;
import java.time.LocalDateTime;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 프로필 이미지 업로드 URL 발급, 업로드 확정, 삭제 API를 실제 MVC 흐름에서 검증한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ProfileImageApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private ProfileImageStorage profileImageStorage;

    private Member worker;

    /**
     * 각 테스트가 독립적으로 실행되도록 회원과 프로필 이미지 데이터를 초기화한다.
     */
    @BeforeEach
    void setUp() {
        ensureProfileImageGeneratedUniqueConstraints();
        cleanupDatabase();
        worker = memberRepository.save(Member.create(
                SocialProvider.KAKAO,
                "profile-image-worker-subject",
                null,
                "worker",
                "01020000000",
                MemberRole.WORKER
        ));
    }

    /**
     * 로그인 회원은 검증된 이미지 메타데이터로 S3 업로드 URL을 발급받을 수 있다.
     */
    @Test
    void authenticatedMemberCreatesProfileImageUploadUrl() throws Exception {
        when(profileImageStorage.createUploadUrl(any(ProfileImageUploadTarget.class)))
                .thenReturn(new ProfileImageUploadUrl(
                        "https://upload.example.com/profile",
                        "profile-images/1/stored-image.png",
                        "stored-image.png",
                        Map.of("Content-Type", "image/png"),
                        300
                ));

        mockMvc.perform(post("/api/members/me/profile-image/upload-url")
                        .header(HttpHeaders.AUTHORIZATION, bearer(worker))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "originalFileName": "profile.png",
                                  "contentType": "image/png",
                                  "fileSize": 1024
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.uploadUrl").value("https://upload.example.com/profile"))
                .andExpect(jsonPath("$.objectKey").value("profile-images/1/stored-image.png"))
                .andExpect(jsonPath("$.storedFileName").value("stored-image.png"))
                .andExpect(jsonPath("$.expiresInSeconds").value(300));

        Integer pendingCount = jdbcTemplate.queryForObject(
                "select count(*) from profile_image where member_id = ? and status = 'PENDING' and deleted_at is null",
                Integer.class,
                worker.getId()
        );
        org.assertj.core.api.Assertions.assertThat(pendingCount).isEqualTo(1);
    }

    /**
     * 기존 PENDING 업로드가 남아 있어도 새 업로드 URL을 다시 발급할 수 있고 이전 PENDING row는 삭제 상태로 전환한다.
     */
    @Test
    void creatingUploadUrlAgainDeletesPreviousPendingImageBeforeCreatingNewPendingImage() throws Exception {
        String previousObjectKey = "profile-images/%d/failed-upload.png".formatted(worker.getId());
        insertPendingProfileImage(previousObjectKey, "failed-upload.png");
        when(profileImageStorage.createUploadUrl(any(ProfileImageUploadTarget.class)))
                .thenReturn(new ProfileImageUploadUrl(
                        "https://upload.example.com/profile-again",
                        "profile-images/%d/new-upload.png".formatted(worker.getId()),
                        "new-upload.png",
                        Map.of("Content-Type", "image/png"),
                        300
                ));

        mockMvc.perform(post("/api/members/me/profile-image/upload-url")
                        .header(HttpHeaders.AUTHORIZATION, bearer(worker))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "originalFileName": "profile-again.png",
                                  "contentType": "image/png",
                                  "fileSize": 1024
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.uploadUrl").value("https://upload.example.com/profile-again"));

        Integer pendingCount = jdbcTemplate.queryForObject(
                "select count(*) from profile_image where member_id = ? and status = 'PENDING' and deleted_at is null",
                Integer.class,
                worker.getId()
        );
        Integer deletedPreviousCount = jdbcTemplate.queryForObject(
                "select count(*) from profile_image where member_id = ? and object_key = ? and status = 'DELETED'",
                Integer.class,
                worker.getId(),
                previousObjectKey
        );
        org.assertj.core.api.Assertions.assertThat(pendingCount).isEqualTo(1);
        org.assertj.core.api.Assertions.assertThat(deletedPreviousCount).isEqualTo(1);
        verify(profileImageStorage).deleteObject(previousObjectKey);
    }

    /**
     * S3 업로드 완료 후 확정 API를 호출하면 실제 이미지 여부를 검증하고 현재 프로필 이미지로 저장한다.
     */
    @Test
    void authenticatedMemberConfirmsUploadedProfileImage() throws Exception {
        insertPendingProfileImage(
                "profile-images/%d/stored-image.png".formatted(worker.getId()),
                "profile.png"
        );
        when(profileImageStorage.getObjectMetadata("profile-images/%d/stored-image.png".formatted(worker.getId())))
                .thenReturn(new ProfileImageObjectMetadata(
                        "image/png",
                        1024,
                        bytes(0x89, 0x50, 0x4E, 0x47)
                ));

        mockMvc.perform(put("/api/members/me/profile-image")
                        .header(HttpHeaders.AUTHORIZATION, bearer(worker))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "objectKey": "profile-images/%d/stored-image.png"
                                }
                                """.formatted(worker.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profileImage.objectKey").value("profile-images/%d/stored-image.png".formatted(worker.getId())))
                .andExpect(jsonPath("$.profileImage.originalFileName").value("profile.png"))
                .andExpect(jsonPath("$.profileImage.contentType").value("image/png"))
                .andExpect(jsonPath("$.profileImage.fileSize").value(1024));

        Integer activeCount = jdbcTemplate.queryForObject(
                "select count(*) from profile_image where member_id = ? and status = 'ACTIVE' and deleted_at is null",
                Integer.class,
                worker.getId()
        );
        org.assertj.core.api.Assertions.assertThat(activeCount).isEqualTo(1);
    }

    /**
     * 삭제된 과거 프로필 이미지 row가 있어도 새 이미지를 확정하면 새 활성 row를 생성한다.
     */
    @Test
    void authenticatedMemberConfirmsNewProfileImageAfterDeletedHistoryExists() throws Exception {
        String deletedObjectKey = "profile-images/%d/deleted-image.png".formatted(worker.getId());
        insertDeletedProfileImage(deletedObjectKey);

        String newObjectKey = "profile-images/%d/new-image.png".formatted(worker.getId());
        insertPendingProfileImage(newObjectKey, "new-profile.png");
        when(profileImageStorage.getObjectMetadata(newObjectKey))
                .thenReturn(new ProfileImageObjectMetadata(
                        "image/png",
                        1024,
                        bytes(0x89, 0x50, 0x4E, 0x47)
                ));

        mockMvc.perform(put("/api/members/me/profile-image")
                        .header(HttpHeaders.AUTHORIZATION, bearer(worker))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "objectKey": "%s"
                                }
                                """.formatted(newObjectKey)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profileImage.objectKey").value(newObjectKey));

        Integer totalCount = jdbcTemplate.queryForObject(
                "select count(*) from profile_image where member_id = ?",
                Integer.class,
                worker.getId()
        );
        Integer activeCount = jdbcTemplate.queryForObject(
                "select count(*) from profile_image where member_id = ? and status = 'ACTIVE' and deleted_at is null",
                Integer.class,
                worker.getId()
        );
        org.assertj.core.api.Assertions.assertThat(totalCount).isEqualTo(2);
        org.assertj.core.api.Assertions.assertThat(activeCount).isEqualTo(1);
    }

    /**
     * 업로드된 S3 객체가 이미지가 아니면 확정을 거절하고 해당 S3 객체 삭제를 요청한다.
     */
    @Test
    void invalidUploadedProfileImageIsRejectedAndDeleted() throws Exception {
        String objectKey = "profile-images/%d/not-image.png".formatted(worker.getId());
        insertPendingProfileImage(objectKey, "profile.png");
        when(profileImageStorage.getObjectMetadata(objectKey))
                .thenReturn(new ProfileImageObjectMetadata("image/png", 1024, "not-image".getBytes()));

        mockMvc.perform(put("/api/members/me/profile-image")
                        .header(HttpHeaders.AUTHORIZATION, bearer(worker))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "objectKey": "%s"
                                }
                                """.formatted(objectKey)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("4001"));

        verify(profileImageStorage).deleteObject(objectKey);
    }

    /**
     * 기존 활성 이미지가 있는 교체 확정은 새 PENDING row를 ACTIVE로 승격하고 기존 ACTIVE row를 DELETED로 남긴다.
     */
    @Test
    void confirmingPendingProfileImageReplacesPreviousActiveImage() throws Exception {
        String previousObjectKey = "profile-images/%d/previous.png".formatted(worker.getId());
        insertProfileImage(previousObjectKey);
        String nextObjectKey = "profile-images/%d/next.png".formatted(worker.getId());
        insertPendingProfileImage(nextObjectKey, "next.png");
        when(profileImageStorage.getObjectMetadata(nextObjectKey))
                .thenReturn(new ProfileImageObjectMetadata(
                        "image/png",
                        1024,
                        bytes(0x89, 0x50, 0x4E, 0x47)
                ));

        mockMvc.perform(put("/api/members/me/profile-image")
                        .header(HttpHeaders.AUTHORIZATION, bearer(worker))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "objectKey": "%s"
                                }
                                """.formatted(nextObjectKey)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profileImage.objectKey").value(nextObjectKey));

        Integer activeCount = jdbcTemplate.queryForObject(
                "select count(*) from profile_image where member_id = ? and status = 'ACTIVE' and deleted_at is null",
                Integer.class,
                worker.getId()
        );
        Integer deletedPreviousCount = jdbcTemplate.queryForObject(
                "select count(*) from profile_image where member_id = ? and object_key = ? and status = 'DELETED'",
                Integer.class,
                worker.getId(),
                previousObjectKey
        );
        org.assertj.core.api.Assertions.assertThat(activeCount).isEqualTo(1);
        org.assertj.core.api.Assertions.assertThat(deletedPreviousCount).isEqualTo(1);
        verify(profileImageStorage).deleteObject(previousObjectKey);
    }

    /**
     * S3 객체 삭제가 실패해도 DB 변경과 API 응답은 성공해야 한다.
     */
    @Test
    void deleteObjectFailureAfterCommitDoesNotFailProfileImageDeleteApi() throws Exception {
        String objectKey = "profile-images/%d/stored-image.png".formatted(worker.getId());
        insertProfileImage(objectKey);
        doThrow(new IllegalStateException("s3 delete failed"))
                .when(profileImageStorage)
                .deleteObject(objectKey);

        mockMvc.perform(delete("/api/members/me/profile-image")
                        .header(HttpHeaders.AUTHORIZATION, bearer(worker)))
                .andExpect(status().isNoContent());

        Integer deletedCount = jdbcTemplate.queryForObject(
                "select count(*) from profile_image where member_id = ? and object_key = ? and status = 'DELETED'",
                Integer.class,
                worker.getId(),
                objectKey
        );
        org.assertj.core.api.Assertions.assertThat(deletedCount).isEqualTo(1);
    }

    /**
     * PENDING row가 없는 객체는 프로필 이미지로 확정할 수 없다.
     */
    @Test
    void uploadedObjectWithoutPendingRowCannotBeConfirmed() throws Exception {
        String objectKey = "profile-images/%d/not-issued.png".formatted(worker.getId());

        mockMvc.perform(put("/api/members/me/profile-image")
                        .header(HttpHeaders.AUTHORIZATION, bearer(worker))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "objectKey": "%s"
                                }
                                """.formatted(objectKey)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("4004"));

        verifyNoMoreInteractions(profileImageStorage);
    }

    /**
     * 로그인 회원은 현재 프로필 이미지를 삭제할 수 있고, DB row는 비활성화된다.
     */
    @Test
    void authenticatedMemberDeletesCurrentProfileImage() throws Exception {
        String objectKey = "profile-images/%d/stored-image.png".formatted(worker.getId());
        insertProfileImage(objectKey);

        mockMvc.perform(delete("/api/members/me/profile-image")
                        .header(HttpHeaders.AUTHORIZATION, bearer(worker)))
                .andExpect(status().isNoContent());

        Integer activeCount = jdbcTemplate.queryForObject(
                "select count(*) from profile_image where member_id = ? and status = 'ACTIVE' and deleted_at is null",
                Integer.class,
                worker.getId()
        );
        verify(profileImageStorage).deleteObject(objectKey);
        org.assertj.core.api.Assertions.assertThat(activeCount).isZero();
    }

    /**
     * 테스트용 access token 값을 생성한다.
     */
    private String bearer(Member member) {
        return "Bearer " + jwtTokenProvider.createToken(member, TokenType.ACCESS, 1800);
    }

    /**
     * 테스트용 활성 프로필 이미지 row를 직접 준비한다.
     */
    private void insertProfileImage(String objectKey) {
        jdbcTemplate.update(
                """
                        insert into profile_image (
                            member_id, original_file_name, stored_file_name, object_key,
                            image_url, content_type, file_size, status, uploaded_at,
                            created_at, updated_at
                        )
                        values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                worker.getId(),
                "profile.png",
                "stored-image.png",
                objectKey,
                "https://cdn.example.com/" + objectKey,
                "image/png",
                1024L,
                "ACTIVE",
                LocalDateTime.now(),
                LocalDateTime.now(),
                LocalDateTime.now()
        );
    }

    /**
     * 테스트용 삭제 완료 프로필 이미지 row를 직접 준비한다.
     */
    private void insertDeletedProfileImage(String objectKey) {
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update(
                """
                        insert into profile_image (
                            member_id, original_file_name, stored_file_name, object_key,
                            image_url, content_type, file_size, status, uploaded_at,
                            created_at, updated_at, deleted_at
                        )
                        values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                worker.getId(),
                "deleted.png",
                "deleted-image.png",
                objectKey,
                "https://cdn.example.com/" + objectKey,
                "image/png",
                1024L,
                "DELETED",
                now.minusDays(1),
                now.minusDays(1),
                now,
                now
        );
    }

    /**
     * 테스트용 업로드 대기 프로필 이미지 row를 직접 준비한다.
     */
    private void insertPendingProfileImage(String objectKey, String originalFileName) {
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update(
                """
                        insert into profile_image (
                            member_id, original_file_name, stored_file_name, object_key,
                            image_url, content_type, file_size, status, uploaded_at,
                            created_at, updated_at
                        )
                        values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                worker.getId(),
                originalFileName,
                objectKey.substring(objectKey.lastIndexOf('/') + 1),
                objectKey,
                "https://cdn.example.com/" + objectKey,
                "image/png",
                1024L,
                "PENDING",
                null,
                now,
                now
        );
    }

    /**
     * 테스트 DB 데이터를 참조 순서에 맞춰 제거한다.
     */
    private void cleanupDatabase() {
        if (tableExists("profile_image")) {
            jdbcTemplate.update("delete from profile_image");
        }
        if (tableExists("notification_delivery")) {
            jdbcTemplate.update("delete from notification_delivery");
        }
        if (tableExists("notification")) {
            jdbcTemplate.update("delete from notification");
        }
        if (tableExists("fcm_token")) {
            jdbcTemplate.update("delete from fcm_token");
        }
        jdbcTemplate.update("delete from member");
    }

    /**
     * Hibernate 테스트 스키마에 운영 DDL의 ACTIVE/PENDING 단건 보장 generated unique 제약을 보강한다.
     */
    private void ensureProfileImageGeneratedUniqueConstraints() {
        if (!tableExists("profile_image") || columnExists("profile_image", "pending_member_id")) {
            return;
        }

        jdbcTemplate.execute(
                """
                        alter table profile_image
                            add column active_member_id BIGINT
                                generated always as (
                                    case
                                        when status = 'ACTIVE' and deleted_at is null then member_id
                                        else null
                                    end
                                ) stored,
                            add column pending_member_id BIGINT
                                generated always as (
                                    case
                                        when status = 'PENDING' and deleted_at is null then member_id
                                        else null
                                    end
                                ) stored,
                            add constraint uk_profile_image_active_member unique (active_member_id),
                            add constraint uk_profile_image_pending_member unique (pending_member_id)
                        """
        );
    }

    /**
     * 현재 테스트 스키마에 테이블이 존재하는지 확인한다.
     */
    private boolean tableExists(String tableName) {
        Integer count = jdbcTemplate.queryForObject(
                """
                        select count(*)
                          from information_schema.tables
                         where table_schema = database()
                           and table_name = ?
                        """,
                Integer.class,
                tableName
        );
        return count != null && count > 0;
    }

    /**
     * 현재 테스트 스키마에 컬럼이 존재하는지 확인한다.
     */
    private boolean columnExists(String tableName, String columnName) {
        Integer count = jdbcTemplate.queryForObject(
                """
                        select count(*)
                          from information_schema.columns
                         where table_schema = database()
                           and table_name = ?
                           and column_name = ?
                        """,
                Integer.class,
                tableName,
                columnName
        );
        return count != null && count > 0;
    }

    /**
     * 정수 매직 바이트를 byte 배열로 변환한다.
     */
    private byte[] bytes(int... values) {
        byte[] bytes = new byte[values.length];
        for (int i = 0; i < values.length; i++) {
            bytes[i] = (byte) values[i];
        }
        return bytes;
    }
}
