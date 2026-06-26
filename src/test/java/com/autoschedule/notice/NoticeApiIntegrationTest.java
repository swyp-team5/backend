package com.autoschedule.notice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.autoschedule.auth.jwt.JwtTokenProvider;
import com.autoschedule.crew.domain.Crew;
import com.autoschedule.crew.repository.CrewRepository;
import com.autoschedule.member.domain.Member;
import com.autoschedule.member.domain.MemberRole;
import com.autoschedule.member.domain.SocialProvider;
import com.autoschedule.member.repository.MemberRepository;
import com.autoschedule.notification.service.FcmDeliveryProcessor;
import com.autoschedule.workplace.domain.WorkPlace;
import com.autoschedule.workplace.domain.WorkPlaceSize;
import com.autoschedule.workplace.repository.WorkPlaceRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 사업장 공지와 공지 댓글 API가 권한, 빈 상태, 대표 공지, 삭제 상태, 커서 조회를 만족하는지 검증한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class NoticeApiIntegrationTest {

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
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private FcmDeliveryProcessor fcmDeliveryProcessor;

    private Member owner;
    private Member worker;
    private Member outsiderWorker;
    private WorkPlace workPlace;

    /**
     * 테스트 Redis 컨테이너 접속 정보를 주입한다.
     */
    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    /**
     * 각 테스트가 독립적으로 실행되도록 공지, 소속, 사업장, 회원 데이터를 초기화한다.
     */
    @BeforeEach
    void setUp() {
        cleanupDatabase();

        owner = memberRepository.save(Member.create(
                SocialProvider.GOOGLE,
                "notice-owner-subject",
                "owner@test.com",
                "owner",
                "01010000000",
                MemberRole.OWNER
        ));
        worker = memberRepository.save(Member.create(
                SocialProvider.KAKAO,
                "notice-worker-subject",
                "worker@test.com",
                "worker",
                "01020000000",
                MemberRole.WORKER
        ));
        outsiderWorker = memberRepository.save(Member.create(
                SocialProvider.APPLE,
                "notice-outsider-subject",
                null,
                "outsider",
                "01030000000",
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
        crewRepository.save(Crew.createWorker(worker, workPlace));
    }

    /**
     * 사장님은 본인 사업장에 공지를 작성하고, 소속 근무자는 상세 공지를 조회할 수 있다.
     */
    @Test
    void ownerCreatesNoticeAndWorkerReadsDetail() throws Exception {
        JsonNode notice = createNotice(owner, workPlace.getId(), "오늘 마감", "쓰레기 버리고 퇴근해주세요.", true);

        assertThat(notice.get("noticeId").asLong()).isPositive();
        assertThat(notice.get("representative").asBoolean()).isTrue();

        mockMvc.perform(get("/api/notices/{noticeId}", notice.get("noticeId").asLong())
                        .header(HttpHeaders.AUTHORIZATION, bearer(worker)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.noticeId").value(notice.get("noticeId").asLong()))
                .andExpect(jsonPath("$.workPlaceId").value(workPlace.getId()))
                .andExpect(jsonPath("$.title").value("오늘 마감"))
                .andExpect(jsonPath("$.content").value("쓰레기 버리고 퇴근해주세요."))
                .andExpect(jsonPath("$.representative").value(true))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.writerMemberId").value(owner.getId()))
                .andExpect(jsonPath("$.writerMemberName").value("owner"));
    }

    /**
     * 공지가 없는 사업장 목록은 빈 페이지로 조회된다.
     */
    @Test
    void ownerReadsEmptyNoticeList() throws Exception {
        mockMvc.perform(get("/api/work-places/{workPlaceId}/notices", workPlace.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.totalPages").value(0));
    }

    /**
     * 공지 목록은 삭제되지 않은 공지만 최신 생성순으로 조회된다.
     */
    @Test
    void ownerReadsNoticeListLatestFirstExcludingDeleted() throws Exception {
        JsonNode first = createNotice(owner, workPlace.getId(), "첫 공지", "첫 내용", false);
        JsonNode second = createNotice(owner, workPlace.getId(), "둘째 공지", "둘째 내용", false);

        mockMvc.perform(delete("/api/notices/{noticeId}", first.get("noticeId").asLong())
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/work-places/{workPlaceId}/notices", workPlace.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(worker))
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].noticeId").value(second.get("noticeId").asLong()))
                .andExpect(jsonPath("$.content[0].title").value("둘째 공지"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    /**
     * 대표 공지가 없으면 null 대표 공지 응답을 반환한다.
     */
    @Test
    void representativeNoticeIsNullWhenNoneExists() throws Exception {
        mockMvc.perform(get("/api/work-places/{workPlaceId}/notices/representative", workPlace.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(worker)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notice").doesNotExist());
    }

    /**
     * 홈 대표 공지 전용 API는 홈 화면에 필요한 요약 필드만 반환한다.
     */
    @Test
    void homeRepresentativeNoticeReturnsCompactNotice() throws Exception {
        JsonNode representative = createNotice(owner, workPlace.getId(), "홈 대표", "홈 노출 내용", true);

        mockMvc.perform(get("/api/home/work-places/{workPlaceId}/representative-notice", workPlace.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(worker)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notice.noticeId").value(representative.get("noticeId").asLong()))
                .andExpect(jsonPath("$.notice.title").value("홈 대표"))
                .andExpect(jsonPath("$.notice.content").value("홈 노출 내용"))
                .andExpect(jsonPath("$.notice.writerMemberName").value("owner"))
                .andExpect(jsonPath("$.notice.createdAt").exists())
                .andExpect(jsonPath("$.notice.workPlaceId").doesNotExist())
                .andExpect(jsonPath("$.notice.representative").doesNotExist())
                .andExpect(jsonPath("$.notice.status").doesNotExist());
    }

    /**
     * 홈 대표 공지가 없으면 null 대표 공지 응답을 반환한다.
     */
    @Test
    void homeRepresentativeNoticeIsNullWhenNoneExists() throws Exception {
        mockMvc.perform(get("/api/home/work-places/{workPlaceId}/representative-notice", workPlace.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notice").doesNotExist());
    }

    /**
     * 소속되지 않은 근무자는 홈 대표 공지를 조회할 수 없다.
     */
    @Test
    void outsiderWorkerCannotReadHomeRepresentativeNotice() throws Exception {
        createNotice(owner, workPlace.getId(), "홈 대표", "홈 노출 내용", true);

        mockMvc.perform(get("/api/home/work-places/{workPlaceId}/representative-notice", workPlace.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(outsiderWorker)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("4003"));
    }

    /**
     * 홈 최신 공지 API는 대표 여부와 무관하게 가장 최근 작성된 활성 공지 요약을 반환한다.
     */
    @Test
    void homeLatestNoticeReturnsNewestActiveNotice() throws Exception {
        JsonNode representative = createNotice(owner, workPlace.getId(), "대표 공지", "대표 내용", true);
        JsonNode latest = createNotice(owner, workPlace.getId(), "최신 공지", "최신 내용", false);

        mockMvc.perform(get("/api/home/work-places/{workPlaceId}/latest-notice", workPlace.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(worker)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notice.noticeId").value(latest.get("noticeId").asLong()))
                .andExpect(jsonPath("$.notice.title").value("최신 공지"))
                .andExpect(jsonPath("$.notice.content").value("최신 내용"))
                .andExpect(jsonPath("$.notice.writerMemberName").value("owner"))
                .andExpect(jsonPath("$.notice.createdAt").exists())
                .andExpect(jsonPath("$.notice.workPlaceId").doesNotExist())
                .andExpect(jsonPath("$.notice.representative").doesNotExist())
                .andExpect(jsonPath("$.notice.status").doesNotExist());

        mockMvc.perform(get("/api/home/work-places/{workPlaceId}/representative-notice", workPlace.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(worker)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notice.noticeId").value(representative.get("noticeId").asLong()))
                .andExpect(jsonPath("$.notice.title").value("대표 공지"));
    }

    /**
     * 홈 최신 공지가 없으면 null 최신 공지 응답을 반환한다.
     */
    @Test
    void homeLatestNoticeIsNullWhenNoneExists() throws Exception {
        mockMvc.perform(get("/api/home/work-places/{workPlaceId}/latest-notice", workPlace.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notice").doesNotExist());
    }

    /**
     * 소속되지 않은 근무자는 홈 최신 공지를 조회할 수 없다.
     */
    @Test
    void outsiderWorkerCannotReadHomeLatestNotice() throws Exception {
        createNotice(owner, workPlace.getId(), "최신 공지", "최신 내용", false);

        mockMvc.perform(get("/api/home/work-places/{workPlaceId}/latest-notice", workPlace.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(outsiderWorker)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("4003"));
    }

    /**
     * 새 대표 공지를 지정하면 기존 대표 공지는 자동 해제되고 새 공지만 대표로 조회된다.
     */
    @Test
    void representativeNoticeIsReplacedWhenNewRepresentativeIsCreated() throws Exception {
        JsonNode oldRepresentative = createNotice(owner, workPlace.getId(), "기존 대표", "기존 내용", true);
        JsonNode newRepresentative = createNotice(owner, workPlace.getId(), "새 대표", "새 내용", true);

        mockMvc.perform(get("/api/work-places/{workPlaceId}/notices/representative", workPlace.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notice.noticeId").value(newRepresentative.get("noticeId").asLong()))
                .andExpect(jsonPath("$.notice.title").value("새 대표"))
                .andExpect(jsonPath("$.notice.representative").value(true));

        mockMvc.perform(get("/api/notices/{noticeId}", oldRepresentative.get("noticeId").asLong())
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.representative").value(false));
    }

    /**
     * 사장님은 공지를 수정하고 대표 공지 여부를 변경할 수 있다.
     */
    @Test
    void ownerUpdatesNotice() throws Exception {
        JsonNode notice = createNotice(owner, workPlace.getId(), "수정 전", "수정 전 내용", false);

        mockMvc.perform(patch("/api/notices/{noticeId}", notice.get("noticeId").asLong())
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "수정 후",
                                  "content": "수정 후 내용",
                                  "representative": true
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("수정 후"))
                .andExpect(jsonPath("$.content").value("수정 후 내용"))
                .andExpect(jsonPath("$.representative").value(true));
    }

    /**
     * 근무자는 공지를 작성할 수 없다.
     */
    @Test
    void workerCannotCreateNotice() throws Exception {
        mockMvc.perform(post("/api/work-places/{workPlaceId}/notices", workPlace.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(worker))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(noticeBody("근무자 작성", "불가능", false)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("4003"));
    }

    /**
     * 소속되지 않은 근무자는 사업장 공지 목록을 조회할 수 없다.
     */
    @Test
    void ownerCannotCreateNoticeForInactiveWorkPlace() throws Exception {
        jdbcTemplate.update("update work_place set status = 'INACTIVE' where work_place_id = ?", workPlace.getId());

        mockMvc.perform(post("/api/work-places/{workPlaceId}/notices", workPlace.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(noticeBody("inactive work place", "cannot create", false)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("4004"));
    }

    /**
     * 삭제 시각이 기록된 사업장은 소유자에게도 공지 작성 대상이 아닌 리소스로 응답한다.
     */
    @Test
    void ownerCannotCreateNoticeForDeletedWorkPlace() throws Exception {
        jdbcTemplate.update(
                "update work_place set deleted_at = ? where work_place_id = ?",
                LocalDateTime.now(),
                workPlace.getId()
        );

        mockMvc.perform(post("/api/work-places/{workPlaceId}/notices", workPlace.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(noticeBody("deleted work place", "cannot create", false)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("4004"));
    }

    /**
     * 비활성 상태의 사업장은 소유자에게도 공지 작성 대상이 아닌 리소스로 응답한다.
     */
    @Test
    void outsiderWorkerCannotReadNoticeList() throws Exception {
        mockMvc.perform(get("/api/work-places/{workPlaceId}/notices", workPlace.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(outsiderWorker)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("4003"));
    }

    /**
     * 공지 작성 요청의 필수 필드가 비어 있으면 필드 검증 오류를 반환한다.
     */
    @Test
    void noticeCreateRequestValidationFails() throws Exception {
        mockMvc.perform(post("/api/work-places/{workPlaceId}/notices", workPlace.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "",
                                  "content": "",
                                  "representative": false
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("4000"))
                .andExpect(jsonPath("$.errors.length()").value(2));
    }

    /**
     * 사장님은 공지에 여러 댓글을 작성하고 댓글을 커서 기반으로 조회할 수 있다.
     */
    @Test
    void ownerCreatesCommentsAndReadsByCursor() throws Exception {
        JsonNode notice = createNotice(owner, workPlace.getId(), "댓글 공지", "댓글 내용", false);
        JsonNode first = createComment(owner, notice.get("noticeId").asLong(), "첫 댓글");
        JsonNode second = createComment(owner, notice.get("noticeId").asLong(), "둘째 댓글");
        createComment(owner, notice.get("noticeId").asLong(), "셋째 댓글");

        mockMvc.perform(get("/api/notices/{noticeId}/comments", notice.get("noticeId").asLong())
                        .header(HttpHeaders.AUTHORIZATION, bearer(worker))
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].commentId").value(first.get("commentId").asLong()))
                .andExpect(jsonPath("$.content[1].commentId").value(second.get("commentId").asLong()))
                .andExpect(jsonPath("$.nextCursorId").value(second.get("commentId").asLong()))
                .andExpect(jsonPath("$.hasNext").value(true));

        mockMvc.perform(get("/api/notices/{noticeId}/comments", notice.get("noticeId").asLong())
                        .header(HttpHeaders.AUTHORIZATION, bearer(worker))
                        .param("cursorId", String.valueOf(second.get("commentId").asLong()))
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].content").value("셋째 댓글"))
                .andExpect(jsonPath("$.hasNext").value(false));
    }

    /**
     * 댓글이 없는 공지는 빈 댓글 목록으로 조회된다.
     */
    @Test
    void workerReadsEmptyCommentList() throws Exception {
        JsonNode notice = createNotice(owner, workPlace.getId(), "댓글 없음", "내용", false);

        mockMvc.perform(get("/api/notices/{noticeId}/comments", notice.get("noticeId").asLong())
                        .header(HttpHeaders.AUTHORIZATION, bearer(worker)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0))
                .andExpect(jsonPath("$.hasNext").value(false))
                .andExpect(jsonPath("$.nextCursorId").doesNotExist());
    }

    /**
     * 근무자는 댓글을 작성할 수 없다.
     */
    @Test
    void workerCannotCreateComment() throws Exception {
        JsonNode notice = createNotice(owner, workPlace.getId(), "댓글 권한", "내용", false);

        mockMvc.perform(post("/api/notices/{noticeId}/comments", notice.get("noticeId").asLong())
                        .header(HttpHeaders.AUTHORIZATION, bearer(worker))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(commentBody("근무자 댓글")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("4003"));
    }

    /**
     * 사장님은 댓글을 수정하고 삭제할 수 있으며 삭제된 댓글은 조회되지 않는다.
     */
    @Test
    void ownerUpdatesAndDeletesComment() throws Exception {
        JsonNode notice = createNotice(owner, workPlace.getId(), "댓글 수정", "내용", false);
        JsonNode comment = createComment(owner, notice.get("noticeId").asLong(), "수정 전 댓글");

        mockMvc.perform(patch(
                                "/api/notices/{noticeId}/comments/{commentId}",
                                notice.get("noticeId").asLong(),
                                comment.get("commentId").asLong()
                        )
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(commentBody("수정 후 댓글")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("수정 후 댓글"));

        mockMvc.perform(delete(
                                "/api/notices/{noticeId}/comments/{commentId}",
                                notice.get("noticeId").asLong(),
                                comment.get("commentId").asLong()
                        )
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/notices/{noticeId}/comments", notice.get("noticeId").asLong())
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0));
    }

    /**
     * 삭제된 공지에는 댓글을 작성할 수 없다.
     */
    @Test
    void ownerCannotCreateCommentOnDeletedNotice() throws Exception {
        JsonNode notice = createNotice(owner, workPlace.getId(), "삭제 공지", "내용", false);
        mockMvc.perform(delete("/api/notices/{noticeId}", notice.get("noticeId").asLong())
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/notices/{noticeId}/comments", notice.get("noticeId").asLong())
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(commentBody("삭제 공지 댓글")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("4004"));
    }

    /**
     * 사장님이 공지를 작성하면 승인된 근무자에게 앱 알림과 FCM 발송 대기 이력을 생성한다.
     */
    @Test
    void ownerCreatesNoticeAndPushNotificationIsPreparedForApprovedWorkers() throws Exception {
        registerFcmToken(worker, "worker-device", "worker-fcm-token");
        registerFcmToken(outsiderWorker, "outsider-device", "outsider-fcm-token");

        JsonNode notice = createNotice(owner, workPlace.getId(), "새 공지", "확인해주세요.", false);

        Integer workerNotificationCount = jdbcTemplate.queryForObject(
                """
                        select count(*)
                          from notification
                         where receiver_member_id = ?
                           and notification_type = 'NOTICE'
                           and push_policy = 'PUSH'
                        """,
                Integer.class,
                worker.getId()
        );
        Integer ownerNotificationCount = jdbcTemplate.queryForObject(
                "select count(*) from notification where receiver_member_id = ?",
                Integer.class,
                owner.getId()
        );
        Integer outsiderNotificationCount = jdbcTemplate.queryForObject(
                "select count(*) from notification where receiver_member_id = ?",
                Integer.class,
                outsiderWorker.getId()
        );
        Integer pendingDeliveryCount = jdbcTemplate.queryForObject(
                """
                        select count(*)
                          from notification_delivery delivery
                          join notification notification
                            on notification.notification_id = delivery.notification_id
                         where notification.receiver_member_id = ?
                           and delivery.status = 'PENDING'
                           and notification.data ->> '$.noticeId' = ?
                        """,
                Integer.class,
                worker.getId(),
                notice.get("noticeId").asText()
        );

        assertThat(workerNotificationCount).isEqualTo(1);
        assertThat(ownerNotificationCount).isZero();
        assertThat(outsiderNotificationCount).isZero();
        assertThat(pendingDeliveryCount).isEqualTo(1);
        verify(fcmDeliveryProcessor, timeout(1000)).process(any());
    }

    private JsonNode createNotice(
            Member requester,
            Long workPlaceId,
            String title,
            String content,
            boolean representative
    ) throws Exception {
        String response = mockMvc.perform(post("/api/work-places/{workPlaceId}/notices", workPlaceId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(requester))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(noticeBody(title, content, representative)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(response);
    }

    private JsonNode createComment(Member requester, Long noticeId, String content) throws Exception {
        String response = mockMvc.perform(post("/api/notices/{noticeId}/comments", noticeId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(requester))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(commentBody(content)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(response);
    }

    private String noticeBody(String title, String content, boolean representative) {
        return """
                {
                  "title": "%s",
                  "content": "%s",
                  "representative": %s
                }
                """.formatted(title, content, representative);
    }

    private String commentBody(String content) {
        return """
                {
                  "content": "%s"
                }
                """.formatted(content);
    }

    private void registerFcmToken(Member member, String deviceId, String token) {
        jdbcTemplate.update(
                """
                        insert into fcm_token
                            (member_id, device_id, token, platform, app_version, status, last_registered_at, created_at, updated_at, deleted_at)
                        values
                            (?, ?, ?, 'ANDROID', '1.0.0', 'ACTIVE', now(), now(), now(), null)
                        """,
                member.getId(),
                deviceId,
                token
        );
    }

    private String bearer(Member member) {
        return "Bearer " + jwtTokenProvider.issue(member).accessToken();
    }

    private void cleanupDatabase() {
        com.autoschedule.support.TestDatabaseCleaner.clean(jdbcTemplate);
    }

    private void deleteTableIfExists(String tableName) {
        Integer tableCount = jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*)
                        FROM information_schema.TABLES
                        WHERE TABLE_SCHEMA = DATABASE()
                          AND TABLE_NAME = ?
                        """,
                Integer.class,
                tableName
        );
        if (tableCount != null && tableCount > 0) {
            jdbcTemplate.update("DELETE FROM " + tableName);
        }
    }
}
