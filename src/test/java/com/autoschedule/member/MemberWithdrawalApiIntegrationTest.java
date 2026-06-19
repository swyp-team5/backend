package com.autoschedule.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.autoschedule.auth.domain.DevicePlatform;
import com.autoschedule.auth.domain.TokenType;
import com.autoschedule.auth.jwt.JwtTokenProvider;
import com.autoschedule.auth.refresh.RefreshTokenStore;
import com.autoschedule.member.domain.Member;
import com.autoschedule.member.domain.MemberRole;
import com.autoschedule.member.domain.MemberStatus;
import com.autoschedule.member.domain.SocialProvider;
import com.autoschedule.member.repository.MemberRepository;
import com.autoschedule.notification.domain.FcmToken;
import com.autoschedule.notification.repository.FcmTokenRepository;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 회원탈퇴 신청, 탈퇴 취소, 토큰 정리 정책을 API 레벨에서 검증한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class MemberWithdrawalApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private FcmTokenRepository fcmTokenRepository;

    @MockitoBean
    private RefreshTokenStore refreshTokenStore;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Member worker;

    /**
     * 각 테스트가 독립적으로 실행되도록 회원, FCM 토큰, Redis refresh token을 초기화한다.
     */
    @BeforeEach
    void setUp() {
        cleanupDatabase();
        worker = memberRepository.save(Member.create(
                SocialProvider.GOOGLE,
                "withdrawal-worker-subject",
                "worker@test.com",
                "worker",
                "01020000000",
                MemberRole.WORKER
        ));
    }

    /**
     * 활성 회원은 탈퇴 신청 시 유예 상태가 되고 refresh token과 FCM token이 모두 정리된다.
     */
    @Test
    void activeMemberRequestsWithdrawalAndCleansRefreshAndFcmTokens() throws Exception {
        FcmToken firstToken = saveFcmToken("device-1", "fcm-token-1");
        FcmToken secondToken = saveFcmToken("device-2", "fcm-token-2");

        mockMvc.perform(delete("/api/members/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer(worker)))
                .andExpect(status().isNoContent());

        Member withdrawnMember = memberRepository.findById(worker.getId()).orElseThrow();
        assertThat(withdrawnMember.getStatus()).isEqualTo(MemberStatus.WITHDRAWAL_PENDING);
        assertThat(withdrawnMember.getDeletedAt()).isNotNull();
        verify(refreshTokenStore).deleteAll(worker.getId());
        assertFcmTokenInactive(firstToken.getId());
        assertFcmTokenInactive(secondToken.getId());
    }

    /**
     * 활성 FCM 토큰이 없어도 회원탈퇴는 성공하고 refresh token 전체 정리 책임은 호출된다.
     */
    @Test
    void activeMemberWithoutFcmTokensRequestsWithdrawal() throws Exception {
        mockMvc.perform(delete("/api/members/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer(worker)))
                .andExpect(status().isNoContent());

        Member withdrawnMember = memberRepository.findById(worker.getId()).orElseThrow();
        assertThat(withdrawnMember.getStatus()).isEqualTo(MemberStatus.WITHDRAWAL_PENDING);
        assertThat(withdrawnMember.getDeletedAt()).isNotNull();
        verify(refreshTokenStore).deleteAll(worker.getId());
    }

    /**
     * 이미 탈퇴 유예 상태인 회원이 다시 탈퇴 신청해도 최초 탈퇴 신청 시각은 유지된다.
     */
    @Test
    void withdrawalRequestIsIdempotentAndPreservesFirstDeletedAt() throws Exception {
        LocalDateTime firstRequestedAt = LocalDateTime.now().minusDays(3).withNano(0);
        markWithdrawalPending(worker.getId(), firstRequestedAt);

        mockMvc.perform(delete("/api/members/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer(worker)))
                .andExpect(status().isNoContent());

        Timestamp deletedAt = jdbcTemplate.queryForObject(
                "select deleted_at from member where member_id = ?",
                Timestamp.class,
                worker.getId()
        );
        assertThat(deletedAt.toLocalDateTime()).isEqualTo(firstRequestedAt);
    }

    /**
     * 탈퇴 유예 30일 이내 회원은 탈퇴 취소 API로 즉시 정상 상태로 복구된다.
     */
    @Test
    void withdrawalPendingMemberCancelsWithinGracePeriod() throws Exception {
        markWithdrawalPending(worker.getId(), LocalDateTime.now().minusDays(29));

        mockMvc.perform(post("/api/members/me/withdrawal-cancel")
                        .header(HttpHeaders.AUTHORIZATION, bearer(worker)))
                .andExpect(status().isNoContent());

        Member restoredMember = memberRepository.findById(worker.getId()).orElseThrow();
        assertThat(restoredMember.getStatus()).isEqualTo(MemberStatus.ACTIVE);
        assertThat(restoredMember.getDeletedAt()).isNull();
    }

    /**
     * 활성 회원이 탈퇴 취소 API를 호출하면 멱등하게 성공하고 상태를 변경하지 않는다.
     */
    @Test
    void activeMemberCancelWithdrawalIsIdempotent() throws Exception {
        mockMvc.perform(post("/api/members/me/withdrawal-cancel")
                        .header(HttpHeaders.AUTHORIZATION, bearer(worker)))
                .andExpect(status().isNoContent());

        Member activeMember = memberRepository.findById(worker.getId()).orElseThrow();
        assertThat(activeMember.getStatus()).isEqualTo(MemberStatus.ACTIVE);
        assertThat(activeMember.getDeletedAt()).isNull();
    }

    /**
     * 탈퇴 신청 후 30일이 지난 회원은 사용자 직접 취소로 복구할 수 없다.
     */
    @Test
    void withdrawalPendingMemberCannotCancelAfterGracePeriod() throws Exception {
        LocalDateTime expiredRequestedAt = LocalDateTime.now().minusDays(31);
        markWithdrawalPending(worker.getId(), expiredRequestedAt);

        mockMvc.perform(post("/api/members/me/withdrawal-cancel")
                        .header(HttpHeaders.AUTHORIZATION, bearer(worker)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("4005"));

        Member pendingMember = memberRepository.findById(worker.getId()).orElseThrow();
        assertThat(pendingMember.getStatus()).isEqualTo(MemberStatus.WITHDRAWAL_PENDING);
        assertThat(pendingMember.getDeletedAt()).isNotNull();
    }

    /**
     * 영구 탈퇴 상태의 회원은 탈퇴 신청을 다시 수행할 수 없다.
     */
    @Test
    void withdrawnMemberCannotRequestWithdrawalAgain() throws Exception {
        markWithdrawn(worker.getId(), LocalDateTime.now().minusDays(31));

        mockMvc.perform(delete("/api/members/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer(worker)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("4005"));

        verifyNoInteractions(refreshTokenStore);
    }

    /**
     * 영구 탈퇴 상태의 회원은 사용자 직접 탈퇴 취소로 복구할 수 없다.
     */
    @Test
    void withdrawnMemberCannotCancelWithdrawal() throws Exception {
        markWithdrawn(worker.getId(), LocalDateTime.now().minusDays(31));

        mockMvc.perform(post("/api/members/me/withdrawal-cancel")
                        .header(HttpHeaders.AUTHORIZATION, bearer(worker)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("4005"));
    }

    /**
     * 인증 토큰이 없으면 회원탈퇴 신청 API에 접근할 수 없다.
     */
    @Test
    void unauthenticatedMemberCannotRequestWithdrawal() throws Exception {
        mockMvc.perform(delete("/api/members/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("4002"));

        verifyNoInteractions(refreshTokenStore);
    }

    /**
     * 인증 토큰이 없으면 회원탈퇴 취소 API에 접근할 수 없다.
     */
    @Test
    void unauthenticatedMemberCannotCancelWithdrawal() throws Exception {
        mockMvc.perform(post("/api/members/me/withdrawal-cancel"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("4002"));
    }

    /**
     * 테스트 회원의 access token 값을 생성한다.
     */
    private String bearer(Member member) {
        return "Bearer " + jwtTokenProvider.createToken(member, TokenType.ACCESS, 1800);
    }

    /**
     * 테스트용 활성 FCM token을 저장한다.
     */
    private FcmToken saveFcmToken(String deviceId, String token) {
        return fcmTokenRepository.save(FcmToken.create(
                worker,
                deviceId,
                token,
                DevicePlatform.ANDROID,
                "1.0.0",
                LocalDateTime.now()
        ));
    }

    /**
     * FCM token이 비활성화되었는지 검증한다.
     */
    private void assertFcmTokenInactive(Long fcmTokenId) {
        MapRow row = jdbcTemplate.queryForObject(
                """
                        select status, deleted_at
                          from fcm_token
                         where fcm_token_id = ?
                        """,
                (rs, rowNum) -> new MapRow(rs.getString("status"), rs.getTimestamp("deleted_at")),
                fcmTokenId
        );
        assertThat(row.status()).isEqualTo("INACTIVE");
        assertThat(row.deletedAt()).isNotNull();
    }

    /**
     * 회원을 탈퇴 유예 상태로 직접 준비한다.
     */
    private void markWithdrawalPending(Long memberId, LocalDateTime deletedAt) {
        jdbcTemplate.update(
                "update member set status = 'WITHDRAWAL_PENDING', deleted_at = ? where member_id = ?",
                deletedAt,
                memberId
        );
    }

    /**
     * 회원을 영구 탈퇴 상태로 직접 준비한다.
     */
    private void markWithdrawn(Long memberId, LocalDateTime deletedAt) {
        jdbcTemplate.update(
                "update member set status = 'WITHDRAWN', deleted_at = ? where member_id = ?",
                deletedAt,
                memberId
        );
    }

    /**
     * 테스트 DB 데이터를 참조 순서에 맞춰 제거한다.
     */
    private void cleanupDatabase() {
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

    private record MapRow(
            String status,
            Timestamp deletedAt
    ) {
    }
}
