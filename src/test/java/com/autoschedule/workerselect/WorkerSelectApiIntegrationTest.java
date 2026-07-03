package com.autoschedule.workerselect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.autoschedule.auth.domain.TokenType;
import com.autoschedule.auth.jwt.JwtTokenProvider;
import com.autoschedule.crew.domain.Crew;
import com.autoschedule.crew.repository.CrewRepository;
import com.autoschedule.member.domain.Member;
import com.autoschedule.member.domain.MemberRole;
import com.autoschedule.member.domain.SocialProvider;
import com.autoschedule.member.repository.MemberRepository;
import com.autoschedule.schedulecondition.domain.Day;
import com.autoschedule.schedulecondition.domain.TimeDetail;
import com.autoschedule.schedulecondition.domain.WeekSchedule;
import com.autoschedule.schedulecondition.repository.DayRepository;
import com.autoschedule.schedulecondition.repository.TimeDetailRepository;
import com.autoschedule.schedulecondition.repository.WeekScheduleRepository;
import com.autoschedule.workerselect.domain.WorkerSelectSubmissionStatus;
import com.autoschedule.workplace.domain.WorkPlace;
import com.autoschedule.workplace.domain.WorkPlaceSize;
import com.autoschedule.workplace.repository.WorkPlaceRepository;
import com.autoschedule.workerselect.domain.WorkerSelectSubmission;
import com.autoschedule.workerselect.domain.WorkerUnavailableTimeDetail;
import com.autoschedule.workerselect.repository.WorkerSelectSubmissionRepository;
import com.autoschedule.workerselect.repository.WorkerUnavailableTimeDetailRepository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 근무자 불가 타임 제출 및 제출 현황 조회 API가 명세에 맞는 응답과 영속 상태를 만드는지 검증한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class WorkerSelectApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private WorkPlaceRepository workPlaceRepository;

    @Autowired
    private CrewRepository crewRepository;

    @Autowired
    private WeekScheduleRepository weekScheduleRepository;

    @Autowired
    private DayRepository dayRepository;

    @Autowired
    private TimeDetailRepository timeDetailRepository;

    @Autowired
    private WorkerSelectSubmissionRepository workerSelectSubmissionRepository;

    @Autowired
    private WorkerUnavailableTimeDetailRepository workerUnavailableTimeDetailRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Member owner;
    private Member worker;
    private WorkPlace workPlace;
    private WeekSchedule weekSchedule;
    private TimeDetail timeDetail;

    @BeforeEach
    void setUp() {
        cleanupDatabase();

        owner = memberRepository.save(Member.create(
                SocialProvider.GOOGLE,
                "owner-subject",
                "owner@test.com",
                "사장님",
                "01011112222",
                MemberRole.OWNER
        ));

        worker = memberRepository.save(Member.create(
                SocialProvider.GOOGLE,
                "worker-subject",
                "worker@test.com",
                "근무자",
                "01033334444",
                MemberRole.WORKER
        ));

        workPlace = workPlaceRepository.save(WorkPlace.create(
                owner.getId(),
                WorkPlaceSize.ONE_TO_FOUR,
                "테스트 가게",
                "서울시 강남구 테헤란로 1",
                null
        ));

        crewRepository.save(Crew.createOwner(owner, workPlace));
        crewRepository.save(Crew.createWorker(worker, workPlace));

        // 타임 상세 정보 시드 (week_schedule → day → time_detail 순서로 생성)
        weekSchedule = weekScheduleRepository.save(WeekSchedule.create(
                workPlace,
                "2025년 7월 1주차",
                LocalDate.now().plusDays(3),
                LocalTime.of(9, 0),
                LocalTime.of(22, 0),
                1,
                5
        ));

        Day day = dayRepository.save(Day.create(
                weekSchedule,
                com.autoschedule.schedulecondition.domain.ScheduleDayName.MONDAY,
                LocalDate.of(2025, 7, 7),
                1,
                0,
                false,
                false
        ));

        timeDetail = timeDetailRepository.save(TimeDetail.create(
                day,
                1,
                "오전",
                2,
                LocalTime.of(9, 0),
                LocalTime.of(17, 0),
                60
        ));
    }

    // ─────────────────────────────────────────────
    // 정상 케이스
    // ─────────────────────────────────────────────

    /**
     * 근무자가 불가 타임을 제출하면 201을 반환하고 workPlaceId, memberId를 응답에 포함한다.
     */
    @Test
    void selectWorkerUnavailable_success() throws Exception {
        mockMvc.perform(post("/api/work-places/{workPlaceId}/worker-select", workPlace.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(worker))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buildRequestWithTimeDetails(List.of(timeDetail.getId()))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.workPlaceId").value(workPlace.getId()))
                .andExpect(jsonPath("$.memberId").value(worker.getId()))
                .andExpect(jsonPath("$.timeDetails").isArray())
                .andExpect(jsonPath("$.timeDetails.length()").value(1))
                .andExpect(jsonPath("$.timeDetails[0].timeDetailId").value(timeDetail.getId()));
    }

    /**
     * 근무자가 빈 목록으로 제출하면 201을 반환하고 timeDetails는 빈 배열이다.
     */
    @Test
    void selectWorkerUnavailable_successWithEmptyTimeDetails() throws Exception {
        mockMvc.perform(post("/api/work-places/{workPlaceId}/worker-select", workPlace.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(worker))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buildRequestWithTimeDetails(List.of())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.workPlaceId").value(workPlace.getId()))
                .andExpect(jsonPath("$.memberId").value(worker.getId()))
                .andExpect(jsonPath("$.timeDetails").isArray())
                .andExpect(jsonPath("$.timeDetails.length()").value(0));
    }

    /**
     * 제출 마감 기한이 지난 주간 스케줄에는 근무 불가능 타임을 제출할 수 없다.
     */
    @Test
    void selectWorkerUnavailable_failsWhenDueDatePassed() throws Exception {
        jdbcTemplate.update(
                "update week_schedule set due_date = ? where week_schedule_id = ?",
                LocalDate.now().minusDays(1),
                weekSchedule.getId()
        );

        mockMvc.perform(post("/api/work-places/{workPlaceId}/worker-select", workPlace.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(worker))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buildRequestWithTimeDetails(List.of(timeDetail.getId()))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("4000"));
    }

    /**
     * 제출 후 worker_select_submission에 제출 현황이, worker_unavailable_time_detail에 타임 정보가 저장된다.
     */
    @Test
    void selectWorkerUnavailable_storedInDatabase() throws Exception {
        mockMvc.perform(post("/api/work-places/{workPlaceId}/worker-select", workPlace.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(worker))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buildRequestWithTimeDetails(List.of(timeDetail.getId()))))
                .andExpect(status().isCreated());

        // submission 저장 검증
        List<WorkerSelectSubmission> submissions = workerSelectSubmissionRepository
                .findByWorkPlaceIdAndWeekScheduleIdAndMemberIdInAndStatusAndDeletedAtIsNull(
                        workPlace.getId(),
                        weekSchedule.getId(),
                        List.of(worker.getId()),
                        WorkerSelectSubmissionStatus.ACTIVE
                );
        assertThat(submissions).hasSize(1);
        assertThat(submissions.get(0).getMemberId()).isEqualTo(worker.getId());

        // time_detail 저장 검증
        List<WorkerUnavailableTimeDetail> timeDetails =
                workerUnavailableTimeDetailRepository.findBySubmission_Id(submissions.get(0).getId());
        assertThat(timeDetails).hasSize(1);
        assertThat(timeDetails.get(0).getTimeDetail().getId()).isEqualTo(timeDetail.getId());
    }

    /**
     * 빈 목록 제출 후 worker_select_submission에는 저장되고 worker_unavailable_time_detail에는 저장되지 않는다.
     */
    @Test
    void selectWorkerUnavailable_emptySubmitStoredInDatabase() throws Exception {
        mockMvc.perform(post("/api/work-places/{workPlaceId}/worker-select", workPlace.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(worker))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buildRequestWithTimeDetails(List.of())))
                .andExpect(status().isCreated());

        // submission은 저장됨
        List<WorkerSelectSubmission> submissions = workerSelectSubmissionRepository
                .findByWorkPlaceIdAndWeekScheduleIdAndMemberIdInAndStatusAndDeletedAtIsNull(
                        workPlace.getId(),
                        weekSchedule.getId(),
                        List.of(worker.getId()),
                        WorkerSelectSubmissionStatus.ACTIVE
                );
        assertThat(submissions).hasSize(1);

        // time_detail은 저장되지 않음
        List<WorkerUnavailableTimeDetail> timeDetails =
                workerUnavailableTimeDetailRepository.findBySubmission_Id(submissions.get(0).getId());
        assertThat(timeDetails).isEmpty();
    }

    /**
     * 사장이 근무자 제출 현황을 조회하면 200을 반환하고 근무자 목록을 포함한다.
     */
    @Test
    void getWorkerSelectStatus_success() throws Exception {
        mockMvc.perform(get("/api/work-places/{workPlaceId}/week-schedules/{weekScheduleId}/worker-select/status",
                        workPlace.getId(), weekSchedule.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workPlaceId").value(workPlace.getId()))
                .andExpect(jsonPath("$.weekScheduleId").value(weekSchedule.getId()))
                .andExpect(jsonPath("$.workers").isArray())
                .andExpect(jsonPath("$.workers.length()").value(1));
    }

    /**
     * 근무자가 제출 전에는 submitted=false로 조회된다.
     */
    @Test
    void getWorkerSelectStatus_submittedFalseBeforeSubmission() throws Exception {
        mockMvc.perform(get("/api/work-places/{workPlaceId}/week-schedules/{weekScheduleId}/worker-select/status",
                        workPlace.getId(), weekSchedule.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workers[0].memberId").value(worker.getId()))
                .andExpect(jsonPath("$.workers[0].memberName").value("근무자"))
                .andExpect(jsonPath("$.workers[0].submitted").value(false));
    }

    /**
     * 근무자가 제출하면 submitted=true로 조회된다.
     */
    @Test
    void getWorkerSelectStatus_submittedTrueAfterSubmission() throws Exception {
        mockMvc.perform(post("/api/work-places/{workPlaceId}/worker-select", workPlace.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(worker))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buildRequestWithTimeDetails(List.of(timeDetail.getId()))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/work-places/{workPlaceId}/week-schedules/{weekScheduleId}/worker-select/status",
                        workPlace.getId(), weekSchedule.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workers[0].memberId").value(worker.getId()))
                .andExpect(jsonPath("$.workers[0].submitted").value(true));
    }

    // ─────────────────────────────────────────────
    // 실패 케이스
    // ─────────────────────────────────────────────

    /**
     * 사장 권한으로 제출 요청하면 403을 반환한다. (@WorkerOnly)
     */
    @Test
    void selectWorkerUnavailable_failsWhenOwnerRequests() throws Exception {
        mockMvc.perform(post("/api/work-places/{workPlaceId}/worker-select", workPlace.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buildRequestWithTimeDetails(List.of(timeDetail.getId()))))
                .andExpect(status().isForbidden());
    }

    /**
     * 해당 사업장 크루원이 아닌 근무자가 제출하면 403을 반환한다.
     */
    @Test
    void selectWorkerUnavailable_failsWhenNotCrewMember() throws Exception {
        Member otherWorker = memberRepository.save(Member.create(
                SocialProvider.GOOGLE,
                "other-worker-subject",
                "other@test.com",
                "다른근무자",
                "01055556666",
                MemberRole.WORKER
        ));

        mockMvc.perform(post("/api/work-places/{workPlaceId}/worker-select", workPlace.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(otherWorker))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buildRequestWithTimeDetails(List.of(timeDetail.getId()))))
                .andExpect(status().isForbidden());
    }

    /**
     * 이미 제출한 근무자가 다시 제출하면 400을 반환한다.
     */
    @Test
    void selectWorkerUnavailable_failsWhenAlreadySubmitted() throws Exception {
        mockMvc.perform(post("/api/work-places/{workPlaceId}/worker-select", workPlace.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(worker))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buildRequestWithTimeDetails(List.of(timeDetail.getId()))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/work-places/{workPlaceId}/worker-select", workPlace.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(worker))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buildRequestWithTimeDetails(List.of(timeDetail.getId()))))
                .andExpect(status().isBadRequest());
    }

    /**
     * 존재하지 않는 timeDetailId를 제출하면 404를 반환한다.
     */
    @Test
    void selectWorkerUnavailable_failsWhenTimeDetailNotFound() throws Exception {
        mockMvc.perform(post("/api/work-places/{workPlaceId}/worker-select", workPlace.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(worker))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buildRequestWithTimeDetails(List.of(99999L))))
                .andExpect(status().isNotFound());
    }

    /**
     * 다른 사업장의 timeDetailId를 제출하면 404를 반환한다.
     */
    @Test
    void selectWorkerUnavailable_failsWhenTimeDetailBelongsToOtherWorkPlace() throws Exception {
        WorkPlace otherWorkPlace = workPlaceRepository.save(WorkPlace.create(
                owner.getId(),
                WorkPlaceSize.ONE_TO_FOUR,
                "다른 가게",
                "서울시 강남구 테헤란로 2",
                null
        ));

        crewRepository.save(Crew.createWorker(worker, otherWorkPlace));

        mockMvc.perform(post("/api/work-places/{workPlaceId}/worker-select", otherWorkPlace.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(worker))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buildRequestWithTimeDetails(List.of(timeDetail.getId()))))
                .andExpect(status().isNotFound());
    }

    /**
     * timeDetails가 null이면 빈 리스트로 정규화되어 201을 반환하고 timeDetails는 빈 배열이다.
     */
    @Test
    void selectWorkerUnavailable_successWhenTimeDetailsIsNull() throws Exception {
        mockMvc.perform(post("/api/work-places/{workPlaceId}/worker-select", workPlace.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(worker))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "weekScheduleId": %d, "timeDetails": null }
                                """.formatted(weekSchedule.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.workPlaceId").value(workPlace.getId()))
                .andExpect(jsonPath("$.memberId").value(worker.getId()))
                .andExpect(jsonPath("$.timeDetails").isArray())
                .andExpect(jsonPath("$.timeDetails.length()").value(0));
    }

    /**
     * 해당 사업장 소유자가 아닌 사장이 현황 조회하면 403을 반환한다.
     */
    @Test
    void getWorkerSelectStatus_failsWhenNotOwnerOfWorkPlace() throws Exception {
        Member otherOwner = memberRepository.save(Member.create(
                SocialProvider.GOOGLE,
                "other-owner-subject",
                "other-owner@test.com",
                "다른사장",
                "01077778888",
                MemberRole.OWNER
        ));

        mockMvc.perform(get("/api/work-places/{workPlaceId}/week-schedules/{weekScheduleId}/worker-select/status",
                        workPlace.getId(), weekSchedule.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(otherOwner)))
                .andExpect(status().isForbidden());
    }

    /**
     * 근무자 권한으로 현황 조회하면 403을 반환한다. (@OwnerOnly)
     */
    @Test
    void getWorkerSelectStatus_failsWhenWorkerRequests() throws Exception {
        mockMvc.perform(get("/api/work-places/{workPlaceId}/week-schedules/{weekScheduleId}/worker-select/status",
                        workPlace.getId(), weekSchedule.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(worker)))
                .andExpect(status().isForbidden());
    }

    // ─────────────────────────────────────────────
    // 경계값 케이스
    // ─────────────────────────────────────────────

    /**
     * 중복된 timeDetailId를 제출하면 응답, DB에는 1개만 표현되고 저장된다.
     */
    @Test
    void selectWorkerUnavailable_deduplicatesDuplicateTimeDetailIds() throws Exception {
        mockMvc.perform(post("/api/work-places/{workPlaceId}/worker-select", workPlace.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(worker))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buildRequestWithTimeDetails(List.of(timeDetail.getId(), timeDetail.getId()))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.timeDetails.length()").value(1));

        List<WorkerSelectSubmission> submissions = workerSelectSubmissionRepository
                .findByWorkPlaceIdAndWeekScheduleIdAndMemberIdInAndStatusAndDeletedAtIsNull(
                        workPlace.getId(),
                        weekSchedule.getId(),
                        List.of(worker.getId()),
                        WorkerSelectSubmissionStatus.ACTIVE
                );
        List<WorkerUnavailableTimeDetail> timeDetails =
                workerUnavailableTimeDetailRepository.findBySubmission_Id(submissions.get(0).getId());

        assertThat(timeDetails).hasSize(1);
    }

    /**
     * 존재하지 않는 workPlaceId로 제출하면 404를 반환한다.
     */
    @Test
    void selectWorkerUnavailable_failsWhenWorkPlaceNotFound() throws Exception {
        mockMvc.perform(post("/api/work-places/{workPlaceId}/worker-select", 99999L)
                        .header(HttpHeaders.AUTHORIZATION, bearer(worker))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buildRequestWithTimeDetails(List.of(timeDetail.getId()))))
                .andExpect(status().isNotFound());
    }

    /**
     * 존재하지 않는 workPlaceId로 현황 조회하면 404를 반환한다.
     */
    @Test
    void getWorkerSelectStatus_failsWhenWorkPlaceNotFound() throws Exception {
        mockMvc.perform(get("/api/work-places/{workPlaceId}/week-schedules/{weekScheduleId}/worker-select/status",
                        99999L, weekSchedule.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isNotFound());
    }

    /**
     * 존재하지 않는 weekScheduleId로 현황 조회하면 404를 반환한다.
     */
    @Test
    void getWorkerSelectStatus_failsWhenWeekScheduleNotFound() throws Exception {
        mockMvc.perform(get("/api/work-places/{workPlaceId}/week-schedules/{weekScheduleId}/worker-select/status",
                        workPlace.getId(), 99999L)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isNotFound());
    }

    /**
     * 인증 토큰 없이 제출하면 401을 반환한다.
     */
    @Test
    void selectWorkerUnavailable_failsWhenNoToken() throws Exception {
        mockMvc.perform(post("/api/work-places/{workPlaceId}/worker-select", workPlace.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buildRequestWithTimeDetails(List.of(timeDetail.getId()))))
                .andExpect(status().isUnauthorized());
    }

    // ─────────────────────────────────────────────
    // 예외 케이스
    // ─────────────────────────────────────────────

    /**
     * soft-delete된 사업장으로 제출하면 404를 반환한다.
     */
    @Test
    void selectWorkerUnavailable_failsWhenWorkPlaceDeleted() throws Exception {
        jdbcTemplate.update("update work_place set deleted_at = now(), status = 'INACTIVE' where work_place_id = ?", workPlace.getId());

        mockMvc.perform(post("/api/work-places/{workPlaceId}/worker-select", workPlace.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(worker))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buildRequestWithTimeDetails(List.of(timeDetail.getId()))))
                .andExpect(status().isNotFound());
    }

    // ─────────────────────────────────────────────
    // 동시 생성
    // ─────────────────────────────────────────────

    /**
     * 동일 근무자가 동시에 2번 제출하면 하나만 성공(201)하고 나머지는 실패(400)한다.
     */
    @Test
    void selectWorkerUnavailable_handlesMultipleConcurrentRequests() throws Exception {
        String request = buildRequestWithTimeDetails(List.of(timeDetail.getId()));
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch latch = new CountDownLatch(2);
        List<Integer> statuses = new CopyOnWriteArrayList<>();

        for (int i = 0; i < 2; i++) {
            executor.submit(() -> {
                try {
                    int status = mockMvc.perform(
                                    post("/api/work-places/{workPlaceId}/worker-select", workPlace.getId())
                                            .header(HttpHeaders.AUTHORIZATION, bearer(worker))
                                            .contentType(MediaType.APPLICATION_JSON)
                                            .content(request))
                            .andReturn().getResponse().getStatus();
                    statuses.add(status);
                } catch (Exception e) {
                    statuses.add(500);
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(statuses).containsExactlyInAnyOrder(201, 400);
    }

    /**
     * 같은 사업장이라도 다른 주간 스케줄에는 별도 제출이 가능하다.
     */
    @Test
    void selectWorkerUnavailable_successForDifferentWeekSchedule() throws Exception {
        // 첫 번째 주차 제출
        mockMvc.perform(post("/api/work-places/{workPlaceId}/worker-select", workPlace.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(worker))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buildRequestWithTimeDetails(List.of(timeDetail.getId()))))
                .andExpect(status().isCreated());

        // 두 번째 주차 생성
        WeekSchedule anotherWeekSchedule = weekScheduleRepository.save(WeekSchedule.create(
                workPlace,
                "2025년 7월 2주차",
                LocalDate.now().plusDays(10),
                LocalTime.of(9, 0),
                LocalTime.of(22, 0),
                1,
                5
        ));

        // 두 번째 주차로 요청 — 201이어야 함
        String requestJson = """
                { "weekScheduleId": %d, "timeDetails": [] }
                """.formatted(anotherWeekSchedule.getId());

        mockMvc.perform(post("/api/work-places/{workPlaceId}/worker-select", workPlace.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(worker))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isCreated());
    }

    // ─────────────────────────────────────────────
    // 헬퍼 메서드
    // ─────────────────────────────────────────────

    /**
     * timeDetailIds 목록으로 요청 JSON을 생성한다.
     */
    private String buildRequestWithTimeDetails(List<Long> ids) {
        String idsJson = ids.stream()
                .map(String::valueOf)
                .reduce((a, b) -> a + "," + b)
                .map(s -> "[" + s + "]")
                .orElse("[]");
        return """
                { "weekScheduleId": %d, "timeDetails": %s }
                """.formatted(weekSchedule.getId(), idsJson);
    }

    /**
     * 테스트용 Bearer 토큰을 생성한다.
     */
    private String bearer(Member member) {
        return "Bearer " + jwtTokenProvider.createToken(member, TokenType.ACCESS, 1800);
    }

    /**
     * 테스트 격리를 위해 관련 테이블을 참조 순서에 맞게 삭제한다.
     */
    private void cleanupDatabase() {
        jdbcTemplate.update("delete from worker_unavailable_time_detail");
        jdbcTemplate.update("delete from worker_select_submission");
        jdbcTemplate.update("delete from time_detail");
        jdbcTemplate.update("delete from day");
        jdbcTemplate.update("delete from crew");
        jdbcTemplate.update("delete from week_schedule");
        jdbcTemplate.update("delete from work_place");
        jdbcTemplate.update("delete from member");
    }
}
