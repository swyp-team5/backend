package com.autoschedule.schedule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.autoschedule.auth.domain.TokenType;
import com.autoschedule.auth.jwt.JwtTokenProvider;
import com.autoschedule.crew.domain.Crew;
import com.autoschedule.crew.repository.CrewRepository;
import com.autoschedule.member.domain.Member;
import com.autoschedule.member.domain.MemberRole;
import com.autoschedule.member.domain.ProfileImage;
import com.autoschedule.member.domain.SocialProvider;
import com.autoschedule.member.repository.MemberRepository;
import com.autoschedule.member.repository.ProfileImageRepository;
import com.autoschedule.schedule.domain.ConfirmedScheduleAssignment;
import com.autoschedule.schedule.domain.ConfirmedScheduleAssignmentStatus;
import com.autoschedule.schedule.domain.ConfirmedWeekSchedule;
import com.autoschedule.schedule.domain.ScheduleGenerationRun;
import com.autoschedule.schedule.domain.ScheduleGenerationRunStatus;
import com.autoschedule.schedule.domain.SchedulePreview;
import com.autoschedule.schedule.repository.ConfirmedScheduleAssignmentRepository;
import com.autoschedule.schedule.repository.ConfirmedWeekScheduleRepository;
import com.autoschedule.schedule.repository.ScheduleGenerationRunRepository;
import com.autoschedule.schedule.repository.SchedulePreviewRepository;
import com.autoschedule.schedulecondition.domain.Day;
import com.autoschedule.schedulecondition.domain.ScheduleDayName;
import com.autoschedule.schedulecondition.domain.TimeDetail;
import com.autoschedule.schedulecondition.domain.WeekSchedule;
import com.autoschedule.schedulecondition.repository.DayRepository;
import com.autoschedule.schedulecondition.repository.TimeDetailRepository;
import com.autoschedule.schedulecondition.repository.WeekScheduleRepository;
import com.autoschedule.support.TestDatabaseCleaner;
import com.autoschedule.workerselect.domain.WorkerSelectSubmission;
import com.autoschedule.workerselect.domain.WorkerUnavailableTimeDetail;
import com.autoschedule.workerselect.repository.WorkerSelectSubmissionRepository;
import com.autoschedule.workerselect.repository.WorkerUnavailableTimeDetailRepository;
import com.autoschedule.workplace.domain.WorkPlace;
import com.autoschedule.workplace.domain.WorkPlaceSize;
import com.autoschedule.workplace.repository.WorkPlaceRepository;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
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
 * 자동 스케줄 생성, 미리보기 조회, 확정 API가 핵심 권한과 스케줄 불변식을 지키는지 검증한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ScheduleGenerationApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private ProfileImageRepository profileImageRepository;

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
    private ScheduleGenerationRunRepository scheduleGenerationRunRepository;

    @Autowired
    private SchedulePreviewRepository schedulePreviewRepository;

    @Autowired
    private ConfirmedWeekScheduleRepository confirmedWeekScheduleRepository;

    @Autowired
    private ConfirmedScheduleAssignmentRepository confirmedScheduleAssignmentRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Member owner;
    private Member workerA;
    private Member workerB;
    private WorkPlace workPlace;
    private WeekSchedule weekSchedule;
    private TimeDetail morning;
    private TimeDetail evening;
    private Crew workerBCrew;

    @BeforeEach
    void setUp() {
        TestDatabaseCleaner.clean(jdbcTemplate);

        owner = memberRepository.save(Member.create(
                SocialProvider.GOOGLE,
                "schedule-owner",
                "owner@test.com",
                "사장",
                "01011112222",
                MemberRole.OWNER
        ));
        workerA = memberRepository.save(Member.create(
                SocialProvider.GOOGLE,
                "schedule-worker-a",
                "worker-a@test.com",
                "근무자A",
                "01022223333",
                MemberRole.WORKER
        ));
        workerB = memberRepository.save(Member.create(
                SocialProvider.GOOGLE,
                "schedule-worker-b",
                "worker-b@test.com",
                "근무자B",
                "01033334444",
                MemberRole.WORKER
        ));

        workPlace = workPlaceRepository.save(WorkPlace.create(
                owner.getId(),
                WorkPlaceSize.ONE_TO_FOUR,
                "테스트 매장",
                "서울시 강남구 테스트로 1",
                null
        ));

        crewRepository.save(Crew.createOwner(owner, workPlace));
        crewRepository.save(Crew.createWorker(workerA, workPlace));
        workerBCrew = crewRepository.save(Crew.createWorker(workerB, workPlace));

        LocalDate nextMonday = LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.MONDAY));

        weekSchedule = weekScheduleRepository.save(WeekSchedule.create(
                workPlace,
                "2026년 7월 1주차",
                LocalDate.now().plusDays(3),
                LocalTime.of(9, 0),
                LocalTime.of(22, 0),
                1,
                1
        ));
        Day day = dayRepository.save(Day.create(
                weekSchedule,
                ScheduleDayName.MONDAY,
                nextMonday,
                1,
                1,
                false,
                false
        ));
        for (int plusDays = 1; plusDays < 7; plusDays++) {
            LocalDate date = nextMonday.plusDays(plusDays);
            dayRepository.save(Day.create(
                    weekSchedule,
                    ScheduleDayName.valueOf(date.getDayOfWeek().name()),
                    date,
                    plusDays + 1,
                    1,
                    false,
                    false
            ));
        }
        morning = timeDetailRepository.save(TimeDetail.create(
                day,
                1,
                "오픈",
                1,
                LocalTime.of(9, 0),
                LocalTime.of(13, 0),
                0
        ));
        evening = timeDetailRepository.save(TimeDetail.create(
                day,
                2,
                "마감",
                1,
                LocalTime.of(18, 0),
                LocalTime.of(22, 0),
                0
        ));
    }

    /**
     * 제출 완료 근무자 기준으로 사장은 자동 스케줄을 생성하고 JSON 미리보기 후보를 조회할 수 있다.
     */
    @Test
    void generateSchedulePreview_success() throws Exception {
        submitWorkerUnavailable(workerA, List.of(evening));
        submitWorkerUnavailable(workerB, List.of(morning));

        mockMvc.perform(post("/api/work-places/{workPlaceId}/week-schedules/{weekScheduleId}/schedule-generation-runs",
                        workPlace.getId(), weekSchedule.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.scheduleGenerationRunId").isNumber())
                .andExpect(jsonPath("$.schedulePreviewId").isNumber())
                .andExpect(jsonPath("$.candidateCount").value(1))
                .andExpect(jsonPath("$.status").value("GENERATED"));

        ScheduleGenerationRun run = scheduleGenerationRunRepository.findAll().get(0);
        SchedulePreview preview = schedulePreviewRepository.findAll().get(0);

        assertThat(run.getStatus()).isEqualTo(ScheduleGenerationRunStatus.GENERATED);
        assertThat(run.getTotalPreviewCount()).isEqualTo(1);
        assertThat(preview.getPreviewData()).contains("\"candidateNo\"");
        assertThat(preview.getPreviewData()).contains("\"timeDetailId\": " + morning.getId());
        assertThat(preview.getPreviewData()).contains("\"workerMemberIds\": [" + workerA.getId() + "]");

        mockMvc.perform(get("/api/work-places/{workPlaceId}/week-schedules/{weekScheduleId}/schedule-generation-runs/{runId}/preview",
                        workPlace.getId(), weekSchedule.getId(), run.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scheduleGenerationRunId").value(run.getId()))
                .andExpect(jsonPath("$.candidateCount").value(1))
                .andExpect(jsonPath("$.previewData.candidates[0].candidateNo").value(1));
    }

    /**
     * 자동 스케줄 생성은 승인 근무자 전체가 아니라 제출을 완료한 활성 근무자만 대상으로 수행한다.
     */
    @Test
    void generateSchedulePreview_usesOnlySubmittedWorkers() throws Exception {
        jdbcTemplate.update(
                "update week_schedule set max_personal_work_count = ? where week_schedule_id = ?",
                2,
                weekSchedule.getId()
        );
        submitWorkerUnavailable(workerA, List.of());

        mockMvc.perform(post("/api/work-places/{workPlaceId}/week-schedules/{weekScheduleId}/schedule-generation-runs",
                        workPlace.getId(), weekSchedule.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.candidateCount").value(1))
                .andExpect(jsonPath("$.status").value("GENERATED"));

        SchedulePreview preview = schedulePreviewRepository.findAll().get(0);
        assertThat(preview.getPreviewData()).contains("\"workerMemberIds\": [" + workerA.getId() + "]");
        assertThat(preview.getPreviewData()).doesNotContain("\"workerMemberIds\": [" + workerB.getId());
    }

    /**
     * 근무 제출 불가 요일은 제출 여부와 관계없이 해당 사업장의 활성 근무자 전체에서 랜덤 배정한다.
     */
    @Test
    void generateSchedulePreview_randomlyAssignsSelectLimitDayWithAllActiveWorkers() throws Exception {
        jdbcTemplate.update(
                "update week_schedule set max_personal_work_count = ? where week_schedule_id = ?",
                2,
                weekSchedule.getId()
        );
        Member workerC = createApprovedWorker(
                "schedule-worker-c-random",
                "worker-c-random@test.com",
                "01044445555"
        );
        Day selectLimitDay = dayRepository.findByWeekSchedule_IdAndDateAndStatusAndDeletedAtIsNull(
                weekSchedule.getId(),
                morning.getDay().getDate().plusDays(1),
                com.autoschedule.schedulecondition.domain.DayStatus.ACTIVE
        ).orElseThrow();
        jdbcTemplate.update("update day set select_limit_status = true where day_id = ?", selectLimitDay.getId());
        TimeDetail selectLimitTimeDetail = timeDetailRepository.save(TimeDetail.create(
                selectLimitDay,
                1,
                "공휴일",
                2,
                LocalTime.of(10, 0),
                LocalTime.of(15, 0),
                0
        ));

        submitWorkerUnavailable(workerA, List.of(evening));
        submitWorkerUnavailable(workerB, List.of(morning));

        mockMvc.perform(post("/api/work-places/{workPlaceId}/week-schedules/{weekScheduleId}/schedule-generation-runs",
                        workPlace.getId(), weekSchedule.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("GENERATED"));

        SchedulePreview preview = schedulePreviewRepository.findAll().get(0);

        assertThat(preview.getPreviewData()).contains("\"timeDetailId\": " + selectLimitTimeDetail.getId());
        assertThat(preview.getPreviewData()).containsAnyOf(
                "\"workerMemberIds\": [" + workerA.getId() + ", " + workerB.getId() + "]",
                "\"workerMemberIds\": [" + workerB.getId() + ", " + workerA.getId() + "]",
                "\"workerMemberIds\": [" + workerA.getId() + ", " + workerC.getId() + "]",
                "\"workerMemberIds\": [" + workerC.getId() + ", " + workerA.getId() + "]",
                "\"workerMemberIds\": [" + workerB.getId() + ", " + workerC.getId() + "]",
                "\"workerMemberIds\": [" + workerC.getId() + ", " + workerB.getId() + "]"
        );
        assertThat(preview.getPreviewData()).containsAnyOf(
                String.valueOf(workerB.getId()),
                String.valueOf(workerC.getId())
        );
    }

    /**
     * 같은 주간 스케줄에 활성 자동 생성 결과가 이미 있으면 일반 생성 API는 중복 생성을 막는다.
     */
    @Test
    void generateSchedulePreview_failsWhenActiveGeneratedRunAlreadyExists() throws Exception {
        submitWorkerUnavailable(workerA, List.of(evening));
        submitWorkerUnavailable(workerB, List.of(morning));

        mockMvc.perform(post("/api/work-places/{workPlaceId}/week-schedules/{weekScheduleId}/schedule-generation-runs",
                        workPlace.getId(), weekSchedule.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/work-places/{workPlaceId}/week-schedules/{weekScheduleId}/schedule-generation-runs",
                        workPlace.getId(), weekSchedule.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("4005"));

        assertThat(scheduleGenerationRunRepository.findAll())
                .hasSize(1)
                .allSatisfy(run -> assertThat(run.getStatus()).isEqualTo(ScheduleGenerationRunStatus.GENERATED));
        assertThat(schedulePreviewRepository.findAll()).hasSize(1);
    }

    /**
     * 명시적 재생성 API는 기존 활성 run과 preview를 삭제 처리한 뒤 새 run과 preview를 생성한다.
     */
    @Test
    void regenerateSchedulePreview_marksPreviousRunAndPreviewDeletedThenCreatesNewOnes() throws Exception {
        submitWorkerUnavailable(workerA, List.of(evening));
        submitWorkerUnavailable(workerB, List.of(morning));

        mockMvc.perform(post("/api/work-places/{workPlaceId}/week-schedules/{weekScheduleId}/schedule-generation-runs",
                        workPlace.getId(), weekSchedule.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isCreated());

        ScheduleGenerationRun oldRun = scheduleGenerationRunRepository.findAll().get(0);
        SchedulePreview oldPreview = schedulePreviewRepository.findAll().get(0);

        mockMvc.perform(post("/api/work-places/{workPlaceId}/week-schedules/{weekScheduleId}/schedule-generation-runs/regenerate",
                        workPlace.getId(), weekSchedule.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.scheduleGenerationRunId").isNumber())
                .andExpect(jsonPath("$.schedulePreviewId").isNumber())
                .andExpect(jsonPath("$.status").value("GENERATED"));

        List<ScheduleGenerationRun> runs = scheduleGenerationRunRepository.findAll();
        List<SchedulePreview> previews = schedulePreviewRepository.findAll();

        assertThat(runs).hasSize(2);
        assertThat(previews).hasSize(2);
        assertThat(scheduleGenerationRunRepository.findById(oldRun.getId()).orElseThrow().getStatus())
                .isEqualTo(ScheduleGenerationRunStatus.DELETED);
        assertThat(scheduleGenerationRunRepository.findById(oldRun.getId()).orElseThrow().getDeletedAt()).isNotNull();
        assertThat(schedulePreviewRepository.findById(oldPreview.getId()).orElseThrow().getDeletedAt()).isNotNull();
        assertThat(runs)
                .filteredOn(run -> run.getStatus() == ScheduleGenerationRunStatus.GENERATED)
                .hasSize(1);
    }

    /**
     * 자동 스케줄 생성은 현재 날짜 기준 차주 월~일 스케줄 조건에 대해서만 허용한다.
     */
    @Test
    void generateSchedulePreview_failsWhenWeekScheduleIsNotNextWeek() throws Exception {
        WeekSchedule currentWeekSchedule = createWeekScheduleStartingAt(
                LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        );

        mockMvc.perform(post("/api/work-places/{workPlaceId}/week-schedules/{weekScheduleId}/schedule-generation-runs",
                        workPlace.getId(), currentWeekSchedule.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("자동 스케줄은 다음 주 스케줄 조건으로만 생성할 수 있습니다."));
    }

    /**
     * 자동 스케줄 재생성도 현재 날짜 기준 차주 월~일 스케줄 조건에 대해서만 허용한다.
     */
    @Test
    void regenerateSchedulePreview_failsWhenWeekScheduleIsNotNextWeek() throws Exception {
        WeekSchedule currentWeekSchedule = createWeekScheduleStartingAt(
                LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        );

        mockMvc.perform(post("/api/work-places/{workPlaceId}/week-schedules/{weekScheduleId}/schedule-generation-runs/regenerate",
                        workPlace.getId(), currentWeekSchedule.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("자동 스케줄은 다음 주 스케줄 조건으로만 생성할 수 있습니다."));
    }

    @Test
    void confirmSchedule_success() throws Exception {
        submitWorkerUnavailable(workerA, List.of(evening));
        submitWorkerUnavailable(workerB, List.of(morning));

        mockMvc.perform(post("/api/work-places/{workPlaceId}/week-schedules/{weekScheduleId}/schedule-generation-runs",
                        workPlace.getId(), weekSchedule.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isCreated());

        ScheduleGenerationRun run = scheduleGenerationRunRepository.findAll().get(0);
        SchedulePreview preview = schedulePreviewRepository.findAll().get(0);

        mockMvc.perform(post("/api/work-places/{workPlaceId}/week-schedules/{weekScheduleId}/confirmed-week-schedules",
                        workPlace.getId(), weekSchedule.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "scheduleGenerationRunId": %d,
                                  "schedulePreviewId": %d,
                                  "selectedCandidateNo": 1
                                }
                                """.formatted(run.getId(), preview.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.confirmedWeekScheduleId").isNumber())
                .andExpect(jsonPath("$.assignmentCount").value(2))
                .andExpect(jsonPath("$.selectedCandidateNo").value(1));

        ConfirmedWeekSchedule confirmed = confirmedWeekScheduleRepository.findAll().get(0);
        assertThat(confirmed.getWeekSchedule().getId()).isEqualTo(weekSchedule.getId());
        List<ConfirmedScheduleAssignment> assignments = confirmedScheduleAssignmentRepository.findAll();
        assertThat(assignments).hasSize(2);
        assertThat(assignments)
                .extracting(ConfirmedScheduleAssignment::getStatus)
                .containsOnly(ConfirmedScheduleAssignmentStatus.ACTIVE);
    }

    /**
     * 제출 완료 근무자만으로 스케줄 조건을 만족할 수 없으면 자동 스케줄을 생성할 수 없다.
     */
    @Test
    void generateSchedulePreview_failsWhenSubmittedWorkersCannotSatisfySchedule() throws Exception {
        jdbcTemplate.update(
                "update week_schedule set max_personal_work_count = ? where week_schedule_id = ?",
                2,
                weekSchedule.getId()
        );
        submitWorkerUnavailable(workerA, List.of(evening));

        mockMvc.perform(post("/api/work-places/{workPlaceId}/week-schedules/{weekScheduleId}/schedule-generation-runs",
                        workPlace.getId(), weekSchedule.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("4005"))
                .andExpect(jsonPath("$.message").value(
                        "근무 불가 조건 때문에 '" + evening.getDay().getDate() + " 마감(18:00-22:00)' 시간대에 필요한 인원을 배정할 수 없습니다."
                ));
    }

    /**
     * 전체 필요 근무 횟수가 제출 완료 근무자의 최대 근무 가능 횟수를 넘으면 원인을 구분해 응답한다.
     */
    @Test
    void generateSchedulePreview_failsWhenRequiredWorkCountExceedsMaximumCapacity() throws Exception {
        jdbcTemplate.update(
                "update week_schedule set min_personal_work_count = ?, max_personal_work_count = ? where week_schedule_id = ?",
                0,
                0,
                weekSchedule.getId()
        );
        submitWorkerUnavailable(workerA, List.of());
        submitWorkerUnavailable(workerB, List.of());

        mockMvc.perform(post("/api/work-places/{workPlaceId}/week-schedules/{weekScheduleId}/schedule-generation-runs",
                        workPlace.getId(), weekSchedule.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("4005"))
                .andExpect(jsonPath("$.message").value("전체 필요 근무 횟수가 근무자별 최대 근무 횟수 합계를 초과합니다."));
    }

    /**
     * 비활성 크루는 자동 스케줄 생성 대상 근무자에서 제외한다.
     */
    @Test
    void generateSchedulePreview_excludesInactiveCrewFromRequiredSubmitters() throws Exception {
        Member workerC = createApprovedWorker("schedule-worker-c-inactive", "worker-c-inactive@test.com", "01044445555");
        workerBCrew.deactivate(LocalDateTime.now());
        crewRepository.saveAndFlush(workerBCrew);
        submitWorkerUnavailable(workerA, List.of(evening));
        submitWorkerUnavailable(workerC, List.of(morning));

        mockMvc.perform(post("/api/work-places/{workPlaceId}/week-schedules/{weekScheduleId}/schedule-generation-runs",
                        workPlace.getId(), weekSchedule.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.candidateCount").value(1));
    }

    /**
     * 탈퇴 유예 상태 회원은 자동 스케줄 생성 대상 근무자에서 제외한다.
     */
    @Test
    void generateSchedulePreview_excludesWithdrawalPendingMemberFromRequiredSubmitters() throws Exception {
        Member workerC = createApprovedWorker("schedule-worker-c-withdrawal", "worker-c-withdrawal@test.com", "01055556666");
        workerB.requestWithdrawal(LocalDateTime.now());
        memberRepository.saveAndFlush(workerB);
        submitWorkerUnavailable(workerA, List.of(evening));
        submitWorkerUnavailable(workerC, List.of(morning));

        mockMvc.perform(post("/api/work-places/{workPlaceId}/week-schedules/{weekScheduleId}/schedule-generation-runs",
                        workPlace.getId(), weekSchedule.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.candidateCount").value(1));
    }

    /**
     * 해당 사업장의 사장이 아니면 자동 스케줄 생성 요청을 할 수 없다.
     */
    @Test
    void generateSchedulePreview_failsWhenOwnerDoesNotOwnWorkPlace() throws Exception {
        Member otherOwner = memberRepository.save(Member.create(
                SocialProvider.GOOGLE,
                "other-schedule-owner",
                "other-owner@test.com",
                "다른사장",
                "01099998888",
                MemberRole.OWNER
        ));

        mockMvc.perform(post("/api/work-places/{workPlaceId}/week-schedules/{weekScheduleId}/schedule-generation-runs",
                        workPlace.getId(), weekSchedule.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(otherOwner)))
                .andExpect(status().isForbidden());
    }

    /**
     * JSON 미리보기 안에 존재하지 않는 후보 번호를 확정하려 하면 실패한다.
     */
    @Test
    void confirmSchedule_failsWhenSelectedCandidateNoDoesNotExist() throws Exception {
        submitWorkerUnavailable(workerA, List.of(evening));
        submitWorkerUnavailable(workerB, List.of(morning));

        mockMvc.perform(post("/api/work-places/{workPlaceId}/week-schedules/{weekScheduleId}/schedule-generation-runs",
                        workPlace.getId(), weekSchedule.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isCreated());

        ScheduleGenerationRun run = scheduleGenerationRunRepository.findAll().get(0);
        SchedulePreview preview = schedulePreviewRepository.findAll().get(0);

        mockMvc.perform(post("/api/work-places/{workPlaceId}/week-schedules/{weekScheduleId}/confirmed-week-schedules",
                        workPlace.getId(), weekSchedule.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "scheduleGenerationRunId": %d,
                                  "schedulePreviewId": %d,
                                  "selectedCandidateNo": 999
                                }
                                """.formatted(run.getId(), preview.getId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("4001"));
    }

    /**
     * 하나의 주간 스케줄 조건에는 확정 스케줄을 중복 생성할 수 없다.
     */
    @Test
    void confirmSchedule_failsWhenAlreadyConfirmed() throws Exception {
        submitWorkerUnavailable(workerA, List.of(evening));
        submitWorkerUnavailable(workerB, List.of(morning));

        mockMvc.perform(post("/api/work-places/{workPlaceId}/week-schedules/{weekScheduleId}/schedule-generation-runs",
                        workPlace.getId(), weekSchedule.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isCreated());

        ScheduleGenerationRun run = scheduleGenerationRunRepository.findAll().get(0);
        SchedulePreview preview = schedulePreviewRepository.findAll().get(0);
        String request = """
                {
                  "scheduleGenerationRunId": %d,
                  "schedulePreviewId": %d,
                  "selectedCandidateNo": 1
                }
                """.formatted(run.getId(), preview.getId());

        mockMvc.perform(post("/api/work-places/{workPlaceId}/week-schedules/{weekScheduleId}/confirmed-week-schedules",
                        workPlace.getId(), weekSchedule.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/work-places/{workPlaceId}/week-schedules/{weekScheduleId}/confirmed-week-schedules",
                        workPlace.getId(), weekSchedule.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("4005"));
    }

    /**
     * 확정된 주간 스케줄의 날짜에 대해서는 사장이 직접 단건 근무 파트를 추가할 수 있다.
     */
    @Test
    void createManualAssignment_successForConfirmedScheduleDate() throws Exception {
        ConfirmedWeekSchedule confirmed = createConfirmedWeekSchedule();
        LocalDate workDate = morning.getDay().getDate();

        mockMvc.perform(post("/api/work-places/{workPlaceId}/confirmed-week-schedules/{confirmedWeekScheduleId}/assignments",
                        workPlace.getId(), confirmed.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "workDate": "%s",
                                  "workPartNo": 3,
                                  "timeName": "야간",
                                  "startTime": "22:00",
                                  "closeTime": "23:00",
                                  "restTime": 0,
                                  "workerMemberIds": [%d, %d]
                                }
                                """.formatted(workDate, workerA.getId(), workerB.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.confirmedWeekScheduleId").value(confirmed.getId()))
                .andExpect(jsonPath("$.workDate").value(workDate.toString()))
                .andExpect(jsonPath("$.workPartNo").value(3))
                .andExpect(jsonPath("$.workerMemberIds.length()").value(2));

        List<Long> assignedWorkerMemberIds = jdbcTemplate.queryForList("""
                        select csa.worker_member_id
                          from confirmed_schedule_assignment csa
                          join time_detail td on csa.time_detail_id = td.time_detail_id
                         where td.work_part_no = 3
                           and csa.status = 'ACTIVE'
                        """,
                Long.class
        );

        assertThat(assignedWorkerMemberIds).containsExactlyInAnyOrder(workerA.getId(), workerB.getId());
    }

    /**
     * 확정된 주간 스케줄에 포함되지 않는 날짜에는 단건 근무 파트를 추가할 수 없다.
     */
    @Test
    void createManualAssignment_failsWhenWorkDateOutsideConfirmedWeekSchedule() throws Exception {
        ConfirmedWeekSchedule confirmed = createConfirmedWeekSchedule();
        LocalDate outsideDate = morning.getDay().getDate().plusDays(7);

        mockMvc.perform(post("/api/work-places/{workPlaceId}/confirmed-week-schedules/{confirmedWeekScheduleId}/assignments",
                        workPlace.getId(), confirmed.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "workDate": "%s",
                                  "workPartNo": 1,
                                  "timeName": "오픈",
                                  "startTime": "09:00",
                                  "closeTime": "13:00",
                                  "restTime": 0,
                                  "workerMemberIds": [%d]
                                }
                                """.formatted(outsideDate, workerA.getId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("4001"));
    }

    /**
     * 사장이 확정된 근무 파트를 수정하면 기존 time_detail row는 유지하고 배정만 교체한다.
     */
    @Test
    void updateManualAssignment_updatesSameTimeDetailAndReplacesAssignments() throws Exception {
        ConfirmedWeekSchedule confirmed = createConfirmedWeekSchedule();
        LocalDate workDate = morning.getDay().getDate();

        mockMvc.perform(put("/api/work-places/{workPlaceId}/confirmed-week-schedules/{confirmedWeekScheduleId}/time-details/{timeDetailId}/assignments",
                        workPlace.getId(), confirmed.getId(), morning.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "workDate": "%s",
                                  "workPartNo": 4,
                                  "timeName": "수정타임",
                                  "startTime": "10:00",
                                  "closeTime": "14:00",
                                  "restTime": 30,
                                  "workerMemberIds": [%d]
                                }
                                """.formatted(workDate, workerB.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.timeDetailId").value(morning.getId()))
                .andExpect(jsonPath("$.workPartNo").value(4))
                .andExpect(jsonPath("$.workerMemberIds[0]").value(workerB.getId()));

        TimeDetail updatedTimeDetail = timeDetailRepository.findById(morning.getId()).orElseThrow();
        assertThat(updatedTimeDetail.getDeletedAt()).isNull();
        assertThat(updatedTimeDetail.getWorkPartNo()).isEqualTo(4);
        assertThat(updatedTimeDetail.getStartTime()).isEqualTo(LocalTime.of(10, 0));
        assertThat(updatedTimeDetail.getCloseTime()).isEqualTo(LocalTime.of(14, 0));

        Integer deletedOldAssignmentCount = jdbcTemplate.queryForObject("""
                        select count(*)
                          from confirmed_schedule_assignment
                         where time_detail_id = ?
                           and deleted_at is not null
                        """,
                Integer.class,
                morning.getId()
        );
        List<Long> newAssignedWorkerMemberIds = jdbcTemplate.queryForList("""
                        select csa.worker_member_id
                          from confirmed_schedule_assignment csa
                          join time_detail td on csa.time_detail_id = td.time_detail_id
                         where td.time_detail_id = ?
                            and csa.status = 'ACTIVE'
                            and csa.deleted_at is null
                         """,
                Long.class,
                morning.getId()
        );

        assertThat(deletedOldAssignmentCount).isEqualTo(1);
        assertThat(newAssignedWorkerMemberIds).containsExactly(workerB.getId());
    }

    /**
     * 확정된 근무 파트를 수정할 때도 요청 날짜는 확정 주간 스케줄 안에 포함되어야 한다.
     */
    @Test
    void updateManualAssignment_failsWhenWorkDateOutsideConfirmedWeekSchedule() throws Exception {
        ConfirmedWeekSchedule confirmed = createConfirmedWeekSchedule();
        LocalDate outsideDate = morning.getDay().getDate().plusDays(7);

        mockMvc.perform(put("/api/work-places/{workPlaceId}/confirmed-week-schedules/{confirmedWeekScheduleId}/time-details/{timeDetailId}/assignments",
                        workPlace.getId(), confirmed.getId(), morning.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "workDate": "%s",
                                  "workPartNo": 1,
                                  "timeName": "수정타임",
                                  "startTime": "10:00",
                                  "closeTime": "14:00",
                                  "restTime": 30,
                                  "workerMemberIds": [%d]
                                }
                                """.formatted(outsideDate, workerB.getId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("4001"));
    }

    /**
     * 사장이 확정된 근무 파트를 삭제하면 해당 time_detail과 확정 배정도 삭제 처리된다.
     */
    @Test
    void deleteManualAssignment_marksTimeDetailAndAssignmentsDeleted() throws Exception {
        ConfirmedWeekSchedule confirmed = createConfirmedWeekSchedule();

        mockMvc.perform(delete("/api/work-places/{workPlaceId}/confirmed-week-schedules/{confirmedWeekScheduleId}/time-details/{timeDetailId}/assignments",
                        workPlace.getId(), confirmed.getId(), morning.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.timeDetailId").value(morning.getId()))
                .andExpect(jsonPath("$.deletedAssignmentCount").value(1));

        assertThat(timeDetailRepository.findById(morning.getId()).orElseThrow().getDeletedAt()).isNotNull();
        assertThat(confirmedScheduleAssignmentRepository.findAll())
                .filteredOn(assignment -> assignment.getTimeDetail().getId().equals(morning.getId()))
                .allSatisfy(assignment -> assertThat(assignment.getDeletedAt()).isNotNull());
    }

    /**
     * 근무자는 본인에게 배정된 확정 스케줄을 기간 기준 달력 목록으로 조회한다.
     */
    @Test
    void getMyConfirmedSchedules_returnsOnlyMyAssignmentsInDateRange() throws Exception {
        createConfirmedWeekSchedule();
        LocalDate workDate = morning.getDay().getDate();
        LocalDate from = workDate.minusDays(1);
        LocalDate to = workDate.plusDays(30);

        mockMvc.perform(get("/api/me/confirmed-schedules")
                        .param("from", from.toString())
                        .param("to", to.toString())
                        .header(HttpHeaders.AUTHORIZATION, bearer(workerA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.from").value(from.toString()))
                .andExpect(jsonPath("$.to").value(to.toString()))
                .andExpect(jsonPath("$.schedules.length()").value(1))
                .andExpect(jsonPath("$.schedules[0].assignmentId").exists())
                .andExpect(jsonPath("$.schedules[0].workPlaceId").value(workPlace.getId()))
                .andExpect(jsonPath("$.schedules[0].workPlaceName").value(workPlace.getName()))
                .andExpect(jsonPath("$.schedules[0].workDate").value(workDate.toString()))
                .andExpect(jsonPath("$.schedules[0].dayName").value("MONDAY"))
                .andExpect(jsonPath("$.schedules[0].timeDetailId").value(morning.getId()))
                .andExpect(jsonPath("$.schedules[0].timeName").value("오픈"))
                .andExpect(jsonPath("$.schedules[0].workPartNo").value(1))
                .andExpect(jsonPath("$.schedules[0].startTime").value("09:00:00"))
                .andExpect(jsonPath("$.schedules[0].closeTime").value("13:00:00"))
                .andExpect(jsonPath("$.schedules[0].restTime").value(0));
    }

    /**
     * 사장은 자신의 사업장의 주간 확정 스케줄을 날짜와 근무 파트별 근무자 목록으로 조회한다.
     */
    @Test
    void getOwnerWeeklyConfirmedSchedules_returnsWorkPlaceAssignmentsGroupedByDayAndTimeDetail() throws Exception {
        saveActiveProfileImage(workerA, "https://static.example.com/worker-a.png");
        ConfirmedWeekSchedule confirmed = createConfirmedWeekSchedule();
        List<ConfirmedScheduleAssignment> assignments = confirmedScheduleAssignmentRepository
                .findByConfirmedWeekSchedule_IdAndStatusAndDeletedAtIsNullOrderByIdAsc(
                        confirmed.getId(),
                        ConfirmedScheduleAssignmentStatus.ACTIVE
                );
        LocalDate weekStartDate = morning.getDay().getDate();
        LocalDate weekEndDate = weekStartDate.plusDays(6);

        mockMvc.perform(get("/api/work-places/{workPlaceId}/confirmed-schedules/weekly", workPlace.getId())
                        .param("weekStartDate", weekStartDate.toString())
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workPlaceId").value(workPlace.getId()))
                .andExpect(jsonPath("$.weekScheduleId").value(weekSchedule.getId()))
                .andExpect(jsonPath("$.confirmedWeekScheduleId").value(confirmed.getId()))
                .andExpect(jsonPath("$.weekStartDate").value(weekStartDate.toString()))
                .andExpect(jsonPath("$.weekEndDate").value(weekEndDate.toString()))
                .andExpect(jsonPath("$.days.length()").value(1))
                .andExpect(jsonPath("$.days[0].workDate").value(weekStartDate.toString()))
                .andExpect(jsonPath("$.days[0].dayName").value("MONDAY"))
                .andExpect(jsonPath("$.days[0].timeDetails.length()").value(2))
                .andExpect(jsonPath("$.days[0].timeDetails[0].timeDetailId").value(morning.getId()))
                .andExpect(jsonPath("$.days[0].timeDetails[0].timeName").value("오픈"))
                .andExpect(jsonPath("$.days[0].timeDetails[0].workers[0].assignmentId").value(assignments.get(0).getId()))
                .andExpect(jsonPath("$.days[0].timeDetails[0].workers[0].memberId").value(workerA.getId()))
                .andExpect(jsonPath("$.days[0].timeDetails[0].workers[0].name").value(workerA.getName()))
                .andExpect(jsonPath("$.days[0].timeDetails[0].workers[0].profileImageUrl")
                        .value("https://static.example.com/worker-a.png"))
                .andExpect(jsonPath("$.days[0].timeDetails[1].timeDetailId").value(evening.getId()))
                .andExpect(jsonPath("$.days[0].timeDetails[1].workers[0].assignmentId").value(assignments.get(1).getId()))
                .andExpect(jsonPath("$.days[0].timeDetails[1].workers[0].memberId").value(workerB.getId()));
    }

    /**
     * 주간 확정 스케줄은 월요일부터 일요일까지의 1주 단위로만 조회할 수 있다.
     */
    @Test
    void getOwnerWeeklyConfirmedSchedules_failsWhenWeekStartDateIsNotMonday() throws Exception {
        createConfirmedWeekSchedule();
        LocalDate tuesday = morning.getDay().getDate().plusDays(1);

        mockMvc.perform(get("/api/work-places/{workPlaceId}/confirmed-schedules/weekly", workPlace.getId())
                        .param("weekStartDate", tuesday.toString())
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("주간 스케줄은 월요일부터 일요일까지의 기간으로만 조회할 수 있습니다."));
    }

    /**
     * 근무자는 승인된 크루로 속한 사업장의 주간 확정 스케줄을 조회해 교대/대타 대상 배정을 선택할 수 있다.
     */
    @Test
    void getWorkerWeeklyConfirmedSchedules_returnsAssignmentsForWorkChangeRequest() throws Exception {
        saveActiveProfileImage(workerB, "https://static.example.com/worker-b.png");
        ConfirmedWeekSchedule confirmed = createConfirmedWeekSchedule();
        List<ConfirmedScheduleAssignment> assignments = confirmedScheduleAssignmentRepository
                .findByConfirmedWeekSchedule_IdAndStatusAndDeletedAtIsNullOrderByIdAsc(
                        confirmed.getId(),
                        ConfirmedScheduleAssignmentStatus.ACTIVE
                );
        LocalDate weekStartDate = morning.getDay().getDate();

        mockMvc.perform(get("/api/work-places/{workPlaceId}/confirmed-schedules/weekly-workers", workPlace.getId())
                        .param("weekStartDate", weekStartDate.toString())
                        .header(HttpHeaders.AUTHORIZATION, bearer(workerA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workPlaceId").value(workPlace.getId()))
                .andExpect(jsonPath("$.weekStartDate").value(weekStartDate.toString()))
                .andExpect(jsonPath("$.weekEndDate").value(weekStartDate.plusDays(6).toString()))
                .andExpect(jsonPath("$.days.length()").value(1))
                .andExpect(jsonPath("$.days[0].timeDetails.length()").value(2))
                .andExpect(jsonPath("$.days[0].timeDetails[0].workers[0].assignmentId").value(assignments.get(0).getId()))
                .andExpect(jsonPath("$.days[0].timeDetails[0].workers[0].memberId").value(workerA.getId()))
                .andExpect(jsonPath("$.days[0].timeDetails[1].workers[0].assignmentId").value(assignments.get(1).getId()))
                .andExpect(jsonPath("$.days[0].timeDetails[1].workers[0].memberId").value(workerB.getId()))
                .andExpect(jsonPath("$.days[0].timeDetails[1].workers[0].profileImageUrl")
                        .value("https://static.example.com/worker-b.png"));
    }

    /**
     * 근무자는 승인된 크루로 속하지 않은 사업장의 주간 확정 스케줄을 조회할 수 없다.
     */
    @Test
    void getWorkerWeeklyConfirmedSchedules_failsWhenWorkerIsNotActiveCrew() throws Exception {
        createConfirmedWeekSchedule();
        workerBCrew.deactivate(LocalDateTime.now());
        crewRepository.save(workerBCrew);
        LocalDate weekStartDate = morning.getDay().getDate();

        mockMvc.perform(get("/api/work-places/{workPlaceId}/confirmed-schedules/weekly-workers", workPlace.getId())
                        .param("weekStartDate", weekStartDate.toString())
                        .header(HttpHeaders.AUTHORIZATION, bearer(workerB)))
                .andExpect(status().isNotFound());
    }

    @Test
    void getWorkChangeTargetSchedules_returnsConfirmedAssignmentsFromRequestedDateRange() throws Exception {
        saveActiveProfileImage(workerB, "https://static.example.com/worker-b.png");
        ConfirmedWeekSchedule confirmed = createConfirmedWeekSchedule();
        List<ConfirmedScheduleAssignment> assignments = confirmedScheduleAssignmentRepository
                .findByConfirmedWeekSchedule_IdAndStatusAndDeletedAtIsNullOrderByIdAsc(
                        confirmed.getId(),
                        ConfirmedScheduleAssignmentStatus.ACTIVE
                );
        LocalDate fromDate = morning.getDay().getDate();
        LocalDate toDate = fromDate.plusDays(6);

        mockMvc.perform(get("/api/work-places/{workPlaceId}/confirmed-schedules/work-change-targets", workPlace.getId())
                        .param("fromDate", fromDate.toString())
                        .param("toDate", toDate.toString())
                        .header(HttpHeaders.AUTHORIZATION, bearer(workerA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workPlaceId").value(workPlace.getId()))
                .andExpect(jsonPath("$.fromDate").value(fromDate.toString()))
                .andExpect(jsonPath("$.toDate").value(toDate.toString()))
                .andExpect(jsonPath("$.days.length()").value(1))
                .andExpect(jsonPath("$.days[0].workDate").value(fromDate.toString()))
                .andExpect(jsonPath("$.days[0].timeDetails[0].workers[0].assignmentId").value(assignments.get(0).getId()))
                .andExpect(jsonPath("$.days[0].timeDetails[0].workers[0].memberId").value(workerA.getId()))
                .andExpect(jsonPath("$.days[0].timeDetails[1].workers[0].assignmentId").value(assignments.get(1).getId()))
                .andExpect(jsonPath("$.days[0].timeDetails[1].workers[0].memberId").value(workerB.getId()))
                .andExpect(jsonPath("$.days[0].timeDetails[1].workers[0].profileImageUrl")
                        .value("https://static.example.com/worker-b.png"));
    }

    @Test
    void getWorkChangeTargetSchedules_failsWhenWorkerIsNotActiveCrew() throws Exception {
        createConfirmedWeekSchedule();
        workerBCrew.deactivate(LocalDateTime.now());
        crewRepository.save(workerBCrew);
        LocalDate fromDate = morning.getDay().getDate();

        mockMvc.perform(get("/api/work-places/{workPlaceId}/confirmed-schedules/work-change-targets", workPlace.getId())
                        .param("fromDate", fromDate.toString())
                        .param("toDate", fromDate.plusDays(6).toString())
                        .header(HttpHeaders.AUTHORIZATION, bearer(workerB)))
                .andExpect(status().isNotFound());
    }

    @Test
    void getWorkChangeTargetSchedules_failsWhenFromDateIsPast() throws Exception {
        LocalDate yesterday = LocalDate.now().minusDays(1);

        mockMvc.perform(get("/api/work-places/{workPlaceId}/confirmed-schedules/work-change-targets", workPlace.getId())
                        .param("fromDate", yesterday.toString())
                        .header(HttpHeaders.AUTHORIZATION, bearer(workerA)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("교대/대타 대상 근무는 오늘 이후의 확정 근무만 조회할 수 있습니다."));
    }

    @Test
    void getOwnerConfirmedSchedules_returnsWorkPlaceAssignmentsForDateRange() throws Exception {
        saveActiveProfileImage(workerA, "https://static.example.com/worker-a.png");
        createConfirmedWeekSchedule();
        LocalDate workDate = morning.getDay().getDate();
        LocalDate from = workDate.minusDays(1);
        LocalDate to = workDate.plusDays(30);

        mockMvc.perform(get("/api/work-places/{workPlaceId}/confirmed-schedules", workPlace.getId())
                        .param("from", from.toString())
                        .param("to", to.toString())
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workPlaceId").value(workPlace.getId()))
                .andExpect(jsonPath("$.from").value(from.toString()))
                .andExpect(jsonPath("$.to").value(to.toString()))
                .andExpect(jsonPath("$.days.length()").value(1))
                .andExpect(jsonPath("$.days[0].workDate").value(workDate.toString()))
                .andExpect(jsonPath("$.days[0].timeDetails.length()").value(2))
                .andExpect(jsonPath("$.days[0].timeDetails[0].workers[0].memberId").value(workerA.getId()))
                .andExpect(jsonPath("$.days[0].timeDetails[0].workers[0].profileImageUrl")
                        .value("https://static.example.com/worker-a.png"))
                .andExpect(jsonPath("$.days[0].timeDetails[1].workers[0].memberId").value(workerB.getId()));
    }

    /**
     * 근무자별 근무 불가 시간 제출 데이터를 저장한다.
     */
    private void submitWorkerUnavailable(Member worker, List<TimeDetail> unavailableTimeDetails) {
        WorkerSelectSubmission submission = workerSelectSubmissionRepository.save(
                WorkerSelectSubmission.create(workPlace.getId(), weekSchedule.getId(), worker.getId())
        );
        workerUnavailableTimeDetailRepository.saveAll(
                unavailableTimeDetails.stream()
                        .map(timeDetail -> WorkerUnavailableTimeDetail.create(submission, timeDetail))
                        .toList()
        );
    }

    /**
     * 단건 변경 API 테스트를 위해 자동 생성과 확정까지 완료된 주간 스케줄을 만든다.
     */
    private ConfirmedWeekSchedule createConfirmedWeekSchedule() throws Exception {
        submitWorkerUnavailable(workerA, List.of(evening));
        submitWorkerUnavailable(workerB, List.of(morning));

        mockMvc.perform(post("/api/work-places/{workPlaceId}/week-schedules/{weekScheduleId}/schedule-generation-runs",
                        workPlace.getId(), weekSchedule.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isCreated());

        ScheduleGenerationRun run = scheduleGenerationRunRepository.findAll().get(0);
        SchedulePreview preview = schedulePreviewRepository.findAll().get(0);

        mockMvc.perform(post("/api/work-places/{workPlaceId}/week-schedules/{weekScheduleId}/confirmed-week-schedules",
                        workPlace.getId(), weekSchedule.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "scheduleGenerationRunId": %d,
                                  "schedulePreviewId": %d,
                                  "selectedCandidateNo": 1
                                }
                                """.formatted(run.getId(), preview.getId())))
                .andExpect(status().isCreated());

        return confirmedWeekScheduleRepository.findAll().get(0);
    }

    /**
     * 지정한 월요일부터 7일짜리 테스트용 주간 스케줄 조건을 생성한다.
     */
    private WeekSchedule createWeekScheduleStartingAt(LocalDate monday) {
        WeekSchedule createdWeekSchedule = weekScheduleRepository.save(WeekSchedule.create(
                workPlace,
                "TEST-" + monday,
                LocalDate.now(),
                LocalTime.of(9, 0),
                LocalTime.of(22, 0),
                1,
                1
        ));

        for (int plusDays = 0; plusDays < 7; plusDays++) {
            LocalDate date = monday.plusDays(plusDays);
            dayRepository.save(Day.create(
                    createdWeekSchedule,
                    ScheduleDayName.valueOf(date.getDayOfWeek().name()),
                    date,
                    plusDays + 1,
                    1,
                    false,
                    false
            ));
        }

        return createdWeekSchedule;
    }

    /**
     * 테스트용 Bearer 액세스 토큰을 생성한다.
     */
    private String bearer(Member member) {
        return "Bearer " + jwtTokenProvider.createToken(member, TokenType.ACCESS, 1800);
    }

    /**
     * 테스트용 승인 근무자를 생성하고 현재 사업장 크루로 등록한다.
     */
    private Member createApprovedWorker(String socialSubject, String socialEmail, String phoneNumber) {
        Member worker = memberRepository.save(Member.create(
                SocialProvider.GOOGLE,
                socialSubject,
                socialEmail,
                "추가근무자",
                phoneNumber,
                MemberRole.WORKER
        ));
        crewRepository.save(Crew.createWorker(worker, workPlace));
        return worker;
    }

    /**
     * 테스트용 활성 프로필 이미지를 저장한다.
     */
    private void saveActiveProfileImage(Member member, String imageUrl) {
        ProfileImage profileImage = ProfileImage.createPending(
                member,
                member.getName() + ".png",
                member.getName() + "-stored.png",
                "profile-images/" + member.getId() + "/" + member.getName() + ".png",
                imageUrl,
                "image/png",
                1024L
        );
        profileImage.activate("image/png", 1024L, LocalDateTime.now());
        profileImageRepository.save(profileImage);
    }
}
