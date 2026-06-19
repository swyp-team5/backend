package com.autoschedule.crew;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.autoschedule.auth.jwt.JwtTokenProvider;
import com.autoschedule.crew.domain.Crew;
import com.autoschedule.crew.domain.CrewInvitation;
import com.autoschedule.crew.repository.CrewInvitationRepository;
import com.autoschedule.crew.repository.CrewRepository;
import com.autoschedule.member.domain.Member;
import com.autoschedule.member.domain.MemberRole;
import com.autoschedule.member.domain.SocialProvider;
import com.autoschedule.member.repository.MemberRepository;
import com.autoschedule.workplace.domain.WorkPlace;
import com.autoschedule.workplace.domain.WorkPlaceSize;
import com.autoschedule.workplace.repository.WorkPlaceRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 크루 초대 코드 생성과 수락 API가 권한, Redis TTL, DB 상태를 함께 만족하는지 검증한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class CrewInvitationApiIntegrationTest {

    @Container
    private static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7.2-alpine")
            .withExposedPorts(6379);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private WorkPlaceRepository workPlaceRepository;

    @Autowired
    private CrewRepository crewRepository;

    @Autowired
    private CrewInvitationRepository crewInvitationRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Member owner;
    private Member worker;
    private WorkPlace workPlace;

    /**
     * 테스트에서 사용하는 Redis Testcontainers 접속 정보를 주입한다.
     */
    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    /**
     * 각 테스트가 독립적으로 실행되도록 DB와 Redis 상태를 초기화한다.
     */
    @BeforeEach
    void setUp() {
        cleanupDatabase();
        cleanupRedis();

        owner = memberRepository.save(Member.create(
                SocialProvider.GOOGLE,
                "owner-subject",
                "owner@test.com",
                "owner",
                "01000000000",
                MemberRole.OWNER
        ));
        worker = memberRepository.save(Member.create(
                SocialProvider.KAKAO,
                "worker-subject",
                "worker@test.com",
                "worker",
                "01011111111",
                MemberRole.WORKER
        ));
        workPlace = workPlaceRepository.save(WorkPlace.create(
                owner.getId(),
                WorkPlaceSize.FIVE_TO_NINE,
                "store",
                "road address",
                "3F"
        ));
        crewRepository.save(Crew.createOwner(owner, workPlace));
    }

    /**
     * 사장님이 생성한 초대 코드를 근무자가 수락하면 승인된 크루로 즉시 등록된다.
     */
    @Test
    void workerAcceptsOwnerInvitationAndJoinsCrew() throws Exception {
        JsonNode invitation = createInvitation(owner, workPlace.getId());
        String inviteCode = invitation.get("inviteCode").asText();

        mockMvc.perform(post("/api/crew-invitations/{inviteCode}/accept", inviteCode)
                        .header(HttpHeaders.AUTHORIZATION, bearer(worker))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.workPlaceId").value(workPlace.getId()))
                .andExpect(jsonPath("$.workPlaceName").value("store"))
                .andExpect(jsonPath("$.joinStatus").value("APPROVED"))
                .andExpect(jsonPath("$.crewRole").value("WORKER"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        Integer joinedCrewCount = jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*)
                        FROM crew
                        WHERE member_id = ?
                          AND work_place_id = ?
                          AND join_status = 'APPROVED'
                          AND crew_role = 'WORKER'
                          AND status = 'ACTIVE'
                        """,
                Integer.class,
                worker.getId(),
                workPlace.getId()
        );

        assertThat(joinedCrewCount).isOne();
    }

    /**
     * 사장님이 초대 코드를 생성하면 6자리 숫자 코드와 1시간 만료 정보가 반환된다.
     */
    @Test
    void ownerCreatesSixDigitInvitationCode() throws Exception {
        JsonNode invitation = createInvitation(owner, workPlace.getId());

        assertThat(invitation.get("inviteCode").asText()).matches("\\d{6}");
        assertThat(invitation.get("inviteUrl").asText()).isEqualTo(
                "chack-chack://crew-invitations/" + invitation.get("inviteCode").asText()
        );
        assertThat(invitation.get("expiresAt").asText()).isNotBlank();
        assertThat(redisTemplate.getExpire("crew-invitation:" + invitation.get("inviteCode").asText()))
                .isPositive();
    }

    /**
     * 사장님은 자신이 소유하지 않은 사업장에는 초대 코드를 만들 수 없다.
     */
    @Test
    void ownerCannotCreateInvitationForOtherOwnersWorkPlace() throws Exception {
        Member otherOwner = memberRepository.save(Member.create(
                SocialProvider.APPLE,
                "other-owner-subject",
                null,
                "owner2",
                "01022222222",
                MemberRole.OWNER
        ));
        WorkPlace otherWorkPlace = workPlaceRepository.save(WorkPlace.create(
                otherOwner.getId(),
                WorkPlaceSize.ONE_TO_FOUR,
                "other",
                "other road",
                null
        ));

        mockMvc.perform(post("/api/work-places/{workPlaceId}/crew-invitations", otherWorkPlace.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("4004"));
    }

    /**
     * 비활성 상태의 사업장은 소유자에게도 초대 생성 대상이 아닌 리소스로 응답한다.
     */
    @Test
    void ownerCannotCreateInvitationForInactiveWorkPlace() throws Exception {
        jdbcTemplate.update("update work_place set status = 'INACTIVE' where work_place_id = ?", workPlace.getId());

        mockMvc.perform(post("/api/work-places/{workPlaceId}/crew-invitations", workPlace.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("4004"));
    }

    /**
     * 삭제 시각이 기록된 사업장은 소유자에게도 초대 생성 대상이 아닌 리소스로 응답한다.
     */
    @Test
    void ownerCannotCreateInvitationForDeletedWorkPlace() throws Exception {
        jdbcTemplate.update(
                "update work_place set deleted_at = ? where work_place_id = ?",
                LocalDateTime.now(),
                workPlace.getId()
        );

        mockMvc.perform(post("/api/work-places/{workPlaceId}/crew-invitations", workPlace.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("4004"));
    }

    /**
     * OWNER 권한 회원은 근무자 전용 초대 수락 API를 호출할 수 없다.
     */
    @Test
    void ownerCannotAcceptInvitationBecauseAcceptApiIsWorkerOnly() throws Exception {
        JsonNode invitation = createInvitation(owner, workPlace.getId());
        String inviteCode = invitation.get("inviteCode").asText();

        mockMvc.perform(post("/api/crew-invitations/{inviteCode}/accept", inviteCode)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("4003"));
    }

    /**
     * 이미 사업장 크루로 등록된 근무자는 같은 사업장 초대를 다시 수락할 수 없다.
     */
    @Test
    void workerCannotJoinSameWorkPlaceTwice() throws Exception {
        JsonNode firstInvitation = createInvitation(owner, workPlace.getId());
        mockMvc.perform(post("/api/crew-invitations/{inviteCode}/accept", firstInvitation.get("inviteCode").asText())
                        .header(HttpHeaders.AUTHORIZATION, bearer(worker))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isCreated());

        JsonNode secondInvitation = createInvitation(owner, workPlace.getId());

        mockMvc.perform(post("/api/crew-invitations/{inviteCode}/accept", secondInvitation.get("inviteCode").asText())
                        .header(HttpHeaders.AUTHORIZATION, bearer(worker))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("4005"));
    }

    /**
     * 과거 비활성 크루 이력은 현재 활성 소속이 아니므로 초대 수락을 막지 않는다.
     */
    @Test
    void inactiveCrewHistoryDoesNotBlockInvitationAccept() throws Exception {
        Crew inactiveCrew = crewRepository.save(Crew.createWorker(worker, workPlace));
        jdbcTemplate.update(
                "update crew set status = 'INACTIVE', deleted_at = ? where crew_id = ?",
                LocalDateTime.now(),
                inactiveCrew.getId()
        );
        JsonNode invitation = createInvitation(owner, workPlace.getId());

        mockMvc.perform(post("/api/crew-invitations/{inviteCode}/accept", invitation.get("inviteCode").asText())
                        .header(HttpHeaders.AUTHORIZATION, bearer(worker))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        Integer activeCrewCount = jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*)
                        FROM crew
                        WHERE member_id = ?
                          AND work_place_id = ?
                          AND crew_role = 'WORKER'
                          AND status = 'ACTIVE'
                        """,
                Integer.class,
                worker.getId(),
                workPlace.getId()
        );
        assertThat(activeCrewCount).isOne();
    }

    /**
     * Redis 기준 실패 횟수가 5회에 도달한 초대 코드는 잠기고 수락할 수 없다.
     */
    @Test
    void invitationCodeIsLockedAfterFiveFailedAttempts() throws Exception {
        JsonNode invitation = createInvitation(owner, workPlace.getId());
        String inviteCode = invitation.get("inviteCode").asText();
        redisTemplate.opsForValue().set("crew-invitation-attempt:" + inviteCode, "5", Duration.ofHours(1));

        mockMvc.perform(post("/api/crew-invitations/{inviteCode}/accept", inviteCode)
                        .header(HttpHeaders.AUTHORIZATION, bearer(worker))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("4001"));

        String status = jdbcTemplate.queryForObject(
                """
                        SELECT status
                        FROM crew_invitation
                        WHERE invite_code = ?
                        """,
                String.class,
                inviteCode
        );
        assertThat(status).isEqualTo("LOCKED");
    }

    /**
     * 사용 완료된 초대 코드는 다시 사용할 수 없다.
     */
    @Test
    void usedInvitationCodeCannotBeReused() throws Exception {
        JsonNode invitation = createInvitation(owner, workPlace.getId());
        String inviteCode = invitation.get("inviteCode").asText();

        mockMvc.perform(post("/api/crew-invitations/{inviteCode}/accept", inviteCode)
                        .header(HttpHeaders.AUTHORIZATION, bearer(worker))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isCreated());

        Member otherWorker = memberRepository.save(Member.create(
                SocialProvider.APPLE,
                "other-worker-subject",
                null,
                "worker2",
                "01033333333",
                MemberRole.WORKER
        ));

        mockMvc.perform(post("/api/crew-invitations/{inviteCode}/accept", inviteCode)
                        .header(HttpHeaders.AUTHORIZATION, bearer(otherWorker))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("4001"));
    }

    /**
     * 초대 생성 API를 호출하고 JSON 응답을 반환한다.
     */
    /**
     * 같은 초대 코드를 두 근무자가 동시에 수락해도 하나의 요청만 성공한다.
     */
    @Test
    void concurrentAcceptRequestsAllowOnlyOneWorkerToJoin() throws Exception {
        JsonNode invitation = createInvitation(owner, workPlace.getId());
        String inviteCode = invitation.get("inviteCode").asText();
        Member otherWorker = memberRepository.save(Member.create(
                SocialProvider.APPLE,
                "concurrent-worker-subject",
                null,
                "worker2",
                "01044444444",
                MemberRole.WORKER
        ));

        ExecutorService executorService = Executors.newFixedThreadPool(2);
        CountDownLatch readyLatch = new CountDownLatch(2);
        CountDownLatch startLatch = new CountDownLatch(1);

        try {
            Future<Integer> firstResult = executorService.submit(acceptInvitationConcurrently(
                    worker,
                    inviteCode,
                    readyLatch,
                    startLatch
            ));
            Future<Integer> secondResult = executorService.submit(acceptInvitationConcurrently(
                    otherWorker,
                    inviteCode,
                    readyLatch,
                    startLatch
            ));
            readyLatch.await();
            startLatch.countDown();

            List<Integer> statuses = List.of(firstResult.get(), secondResult.get());

            assertThat(statuses).containsExactlyInAnyOrder(201, 400);
            Integer crewCount = jdbcTemplate.queryForObject(
                    """
                            SELECT COUNT(*)
                            FROM crew
                            WHERE work_place_id = ?
                              AND crew_role = 'WORKER'
                              AND join_status = 'APPROVED'
                              AND status = 'ACTIVE'
                            """,
                    Integer.class,
                    workPlace.getId()
            );
            assertThat(crewCount).isOne();

            String invitationStatus = jdbcTemplate.queryForObject(
                    """
                            SELECT status
                            FROM crew_invitation
                            WHERE invite_code = ?
                            """,
                    String.class,
                    inviteCode
            );
            assertThat(invitationStatus).isEqualTo("USED");
        } finally {
            executorService.shutdownNow();
        }
    }

    /**
     * 만료 시간이 지난 초대 코드를 수락하면 초대 상태가 EXPIRED로 기록된다.
     */
    @Test
    void expiredInvitationCodeIsMarkedExpiredWhenWorkerTriesToAccept() throws Exception {
        String expiredInviteCode = "123456";
        crewInvitationRepository.save(CrewInvitation.create(
                workPlace,
                owner.getId(),
                expiredInviteCode,
                LocalDateTime.now().minusMinutes(1)
        ));

        mockMvc.perform(post("/api/crew-invitations/{inviteCode}/accept", expiredInviteCode)
                        .header(HttpHeaders.AUTHORIZATION, bearer(worker))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("4001"));

        String status = jdbcTemplate.queryForObject(
                """
                        SELECT status
                        FROM crew_invitation
                        WHERE invite_code = ?
                        """,
                String.class,
                expiredInviteCode
        );
        assertThat(status).isEqualTo("EXPIRED");
    }

    /**
     * 초대 생성자 ID는 감사용 스냅샷 값이므로 member 테이블 FK 없이 저장한다.
     */
    @Test
    void crewInvitationMemberSnapshotColumnsHaveNoForeignKeyConstraint() {
        Integer foreignKeyCount = jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*)
                        FROM information_schema.KEY_COLUMN_USAGE
                        WHERE TABLE_SCHEMA = DATABASE()
                          AND TABLE_NAME = 'crew_invitation'
                          AND COLUMN_NAME IN ('created_by_member_id', 'used_by_member_id')
                          AND REFERENCED_TABLE_NAME IS NOT NULL
                        """,
                Integer.class
        );

        assertThat(foreignKeyCount).isZero();
    }

    /**
     * 사장님은 본인 사업장에서 생성한 초대 코드 발급/사용 이력을 조회할 수 있다.
     */
    @Test
    void ownerReadsOwnWorkPlaceInvitationHistory() throws Exception {
        JsonNode activeInvitation = createInvitation(owner, workPlace.getId());
        JsonNode usedInvitation = createInvitation(owner, workPlace.getId());

        mockMvc.perform(post("/api/crew-invitations/{inviteCode}/accept", usedInvitation.get("inviteCode").asText())
                        .header(HttpHeaders.AUTHORIZATION, bearer(worker))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/work-places/{workPlaceId}/crew-invitations", workPlace.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].invitationId").value(usedInvitation.get("invitationId").asLong()))
                .andExpect(jsonPath("$.content[0].inviteCode").value(usedInvitation.get("inviteCode").asText()))
                .andExpect(jsonPath("$.content[0].inviteUrl").value(
                        "chack-chack://crew-invitations/" + usedInvitation.get("inviteCode").asText()
                ))
                .andExpect(jsonPath("$.content[0].status").value("USED"))
                .andExpect(jsonPath("$.content[0].usedByMemberId").value(worker.getId()))
                .andExpect(jsonPath("$.content[0].usedByMemberName").value("worker"))
                .andExpect(jsonPath("$.content[0].usedAt").isNotEmpty())
                .andExpect(jsonPath("$.content[1].invitationId").value(activeInvitation.get("invitationId").asLong()))
                .andExpect(jsonPath("$.content[1].status").value("ACTIVE"))
                .andExpect(jsonPath("$.content[1].usedByMemberId").doesNotExist())
                .andExpect(jsonPath("$.content[1].usedByMemberName").doesNotExist())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(1));
    }

    /**
     * 초대 코드가 한 번도 생성되지 않은 사업장도 빈 이력 목록으로 조회된다.
     */
    @Test
    void ownerReadsEmptyInvitationHistory() throws Exception {
        mockMvc.perform(get("/api/work-places/{workPlaceId}/crew-invitations", workPlace.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.totalPages").value(0));
    }

    /**
     * 아직 아무도 수락하지 않은 초대 코드만 있어도 수락자 정보는 null로 조회된다.
     */
    @Test
    void ownerReadsUnusedOnlyInvitationHistory() throws Exception {
        JsonNode activeInvitation = createInvitation(owner, workPlace.getId());

        mockMvc.perform(get("/api/work-places/{workPlaceId}/crew-invitations", workPlace.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].invitationId").value(activeInvitation.get("invitationId").asLong()))
                .andExpect(jsonPath("$.content[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$.content[0].usedAt").doesNotExist())
                .andExpect(jsonPath("$.content[0].usedByMemberId").doesNotExist())
                .andExpect(jsonPath("$.content[0].usedByMemberName").doesNotExist());
    }

    /**
     * 근무자는 사장님 전용 초대 코드 이력 조회 API를 호출할 수 없다.
     */
    @Test
    void workerCannotReadInvitationHistory() throws Exception {
        mockMvc.perform(get("/api/work-places/{workPlaceId}/crew-invitations", workPlace.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(worker)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("4003"));
    }

    /**
     * 사장님은 다른 사장님이 소유한 사업장의 초대 코드 이력을 조회할 수 없다.
     */
    @Test
    void ownerCannotReadOtherOwnersInvitationHistory() throws Exception {
        Member otherOwner = memberRepository.save(Member.create(
                SocialProvider.APPLE,
                "history-other-owner-subject",
                null,
                "owner2",
                "01055555555",
                MemberRole.OWNER
        ));
        WorkPlace otherWorkPlace = workPlaceRepository.save(WorkPlace.create(
                otherOwner.getId(),
                WorkPlaceSize.ONE_TO_FOUR,
                "other",
                "other road",
                null
        ));

        mockMvc.perform(get("/api/work-places/{workPlaceId}/crew-invitations", otherWorkPlace.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("4004"));
    }

    /**
     * 초대 코드 이력 조회의 페이지 파라미터가 허용 범위를 벗어나면 검증 오류를 반환한다.
     */
    @Test
    void invitationHistoryRejectsInvalidPageParameters() throws Exception {
        mockMvc.perform(get("/api/work-places/{workPlaceId}/crew-invitations", workPlace.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .param("page", "-1")
                        .param("size", "20"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("4000"))
                .andExpect(jsonPath("$.errors[0].field").value("page"));
    }

    private JsonNode createInvitation(Member requester, Long workPlaceId) throws Exception {
        String response = mockMvc.perform(post("/api/work-places/{workPlaceId}/crew-invitations", workPlaceId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(requester))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.invitationId").isNumber())
                .andExpect(jsonPath("$.workPlaceId").value(workPlaceId))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(response);
    }

    /**
     * 테스트 회원의 access token을 Authorization 헤더 값으로 변환한다.
     */
    private String bearer(Member member) {
        return "Bearer " + jwtTokenProvider.issue(member).accessToken();
    }

    /**
     * Redis에 남아 있는 인증/초대 관련 테스트 키를 제거한다.
     */
    /**
     * 동시 수락 테스트에서 두 요청이 같은 시점에 출발하도록 대기한 뒤 HTTP 상태 코드를 반환한다.
     */
    private Callable<Integer> acceptInvitationConcurrently(
            Member requester,
            String inviteCode,
            CountDownLatch readyLatch,
            CountDownLatch startLatch
    ) {
        return () -> {
            readyLatch.countDown();
            startLatch.await();
            return mockMvc.perform(post("/api/crew-invitations/{inviteCode}/accept", inviteCode)
                            .header(HttpHeaders.AUTHORIZATION, bearer(requester))
                            .contentType(MediaType.APPLICATION_JSON))
                    .andReturn()
                    .getResponse()
                    .getStatus();
        };
    }

    private void cleanupRedis() {
        deleteKeys("auth:refresh-token:*");
        deleteKeys("crew-invitation:*");
        deleteKeys("crew-invitation-attempt:*");
    }

    /**
     * Redis 패턴에 해당하는 키가 있으면 모두 제거한다.
     */
    private void deleteKeys(String pattern) {
        var keys = redisTemplate.keys(pattern);
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    /**
     * 외래키 순서를 고려해 테스트 DB 데이터를 직접 비운다.
     */
    private void cleanupDatabase() {
        jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 0");
        jdbcTemplate.update("DELETE FROM crew_invitation");
        jdbcTemplate.update("DELETE FROM crew");
        jdbcTemplate.update("DELETE FROM work_place");
        jdbcTemplate.update("DELETE FROM member");
        jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 1");
    }
}
