package com.autoschedule.workchange;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
import com.autoschedule.schedule.domain.ConfirmedScheduleAssignment;
import com.autoschedule.schedule.domain.ConfirmedScheduleAssignmentStatus;
import com.autoschedule.schedule.domain.ConfirmedWeekSchedule;
import com.autoschedule.schedule.repository.ConfirmedScheduleAssignmentRepository;
import com.autoschedule.schedule.repository.ConfirmedWeekScheduleRepository;
import com.autoschedule.schedulecondition.domain.Day;
import com.autoschedule.schedulecondition.domain.ScheduleDayName;
import com.autoschedule.schedulecondition.domain.TimeDetail;
import com.autoschedule.schedulecondition.domain.WeekSchedule;
import com.autoschedule.schedulecondition.repository.DayRepository;
import com.autoschedule.schedulecondition.repository.TimeDetailRepository;
import com.autoschedule.schedulecondition.repository.WeekScheduleRepository;
import com.autoschedule.support.TestDatabaseCleaner;
import com.autoschedule.workplace.domain.WorkPlace;
import com.autoschedule.workplace.domain.WorkPlaceSize;
import com.autoschedule.workplace.repository.WorkPlaceRepository;
import java.time.LocalDate;
import java.time.LocalTime;
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
 * 교대/대타 요청 생성, 대상 근무자 응답, 사장 최종 처리 흐름을 검증한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class WorkChangeRequestApiIntegrationTest {

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
    private Day monday;
    private TimeDetail morning;
    private TimeDetail afternoon;
    private ConfirmedWeekSchedule confirmedWeekSchedule;
    private ConfirmedScheduleAssignment morningAssignmentA;
    private ConfirmedScheduleAssignment afternoonAssignmentB;

    @BeforeEach
    void setUp() {
        TestDatabaseCleaner.clean(jdbcTemplate);

        owner = memberRepository.save(Member.create(
                SocialProvider.GOOGLE,
                "work-change-owner",
                "owner-work-change@test.com",
                "사장",
                "01011112222",
                MemberRole.OWNER
        ));
        workerA = memberRepository.save(Member.create(
                SocialProvider.GOOGLE,
                "work-change-worker-a",
                "worker-a-work-change@test.com",
                "근무자A",
                "01022223333",
                MemberRole.WORKER
        ));
        workerB = memberRepository.save(Member.create(
                SocialProvider.GOOGLE,
                "work-change-worker-b",
                "worker-b-work-change@test.com",
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
        crewRepository.save(Crew.createWorker(workerB, workPlace));

        weekSchedule = weekScheduleRepository.save(WeekSchedule.create(
                workPlace,
                "다음 주 스케줄",
                LocalDate.now().plusDays(3),
                LocalTime.of(9, 0),
                LocalTime.of(22, 0),
                1,
                5
        ));
        monday = dayRepository.save(Day.create(
                weekSchedule,
                ScheduleDayName.MONDAY,
                LocalDate.now().plusDays(3),
                1,
                1,
                false,
                false
        ));
        morning = timeDetailRepository.save(TimeDetail.create(
                monday,
                1,
                "오픈",
                1,
                LocalTime.of(9, 0),
                LocalTime.of(13, 0),
                0
        ));
        afternoon = timeDetailRepository.save(TimeDetail.create(
                monday,
                2,
                "미들",
                1,
                LocalTime.of(14, 0),
                LocalTime.of(18, 0),
                0
        ));
        confirmedWeekSchedule = confirmedWeekScheduleRepository.save(ConfirmedWeekSchedule.create(
                weekSchedule,
                workPlace.getId(),
                null,
                null,
                null,
                owner.getId()
        ));
        morningAssignmentA = confirmedScheduleAssignmentRepository.save(ConfirmedScheduleAssignment.create(
                confirmedWeekSchedule,
                workPlace.getId(),
                weekSchedule,
                monday,
                morning,
                workerA.getId()
        ));
        afternoonAssignmentB = confirmedScheduleAssignmentRepository.save(ConfirmedScheduleAssignment.create(
                confirmedWeekSchedule,
                workPlace.getId(),
                weekSchedule,
                monday,
                afternoon,
                workerB.getId()
        ));
    }

    /**
     * 근무자는 자신의 확정 근무를 다른 근무자에게 대타 요청할 수 있다.
     */
    @Test
    void createSubstituteRequest_success() throws Exception {
        mockMvc.perform(post("/api/work-places/{workPlaceId}/work-change-requests/substitute", workPlace.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(workerA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "requestAssignmentId": %d,
                                  "targetMemberId": %d,
                                  "reason": "개인 일정으로 대타 요청합니다."
                                }
                                """.formatted(morningAssignmentA.getId(), workerB.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.requestType").value("SUBSTITUTE"))
                .andExpect(jsonPath("$.status").value("REQUESTED"))
                .andExpect(jsonPath("$.requesterMemberId").value(workerA.getId()))
                .andExpect(jsonPath("$.targetMemberId").value(workerB.getId()));
    }

    /**
     * 대상 근무자가 수락하고 사장님이 승인하면 기존 배정은 삭제되고 대상 근무자의 새 배정이 생성된다.
     */
    @Test
    void approveSubstituteRequest_reflectsAssignment() throws Exception {
        Long requestId = createSubstituteRequest();

        mockMvc.perform(post("/api/work-places/{workPlaceId}/work-change-requests/{requestId}/accept",
                        workPlace.getId(), requestId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(workerB)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCEPTED_BY_TARGET"));

        mockMvc.perform(post("/api/work-places/{workPlaceId}/owner/work-change-requests/{requestId}/approve",
                        workPlace.getId(), requestId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        ConfirmedScheduleAssignment original = confirmedScheduleAssignmentRepository
                .findById(morningAssignmentA.getId())
                .orElseThrow();
        assertThat(original.getStatus()).isEqualTo(ConfirmedScheduleAssignmentStatus.DELETED);

        boolean targetAssignmentCreated = confirmedScheduleAssignmentRepository.findAll().stream()
                .anyMatch(assignment -> assignment.getWorkerMemberId().equals(workerB.getId())
                        && assignment.getTimeDetail().getId().equals(morning.getId())
                        && assignment.getStatus() == ConfirmedScheduleAssignmentStatus.ACTIVE);
        assertThat(targetAssignmentCreated).isTrue();
    }

    /**
     * 요청자는 대상 근무자가 응답하기 전까지 본인이 보낸 요청을 취소할 수 있다.
     */
    @Test
    void cancelRequestedRequest_success() throws Exception {
        Long requestId = createSubstituteRequest();

        mockMvc.perform(delete("/api/work-places/{workPlaceId}/work-change-requests/{requestId}",
                        workPlace.getId(), requestId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(workerA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELED"));
    }

    /**
     * 대상 근무자가 해당 시간에 이미 근무 중이면 대타 요청을 생성할 수 없다.
     */
    @Test
    void createSubstituteRequest_failWhenTargetAlreadyAssignedAtSameTime() throws Exception {
        confirmedScheduleAssignmentRepository.save(ConfirmedScheduleAssignment.create(
                confirmedWeekSchedule,
                workPlace.getId(),
                weekSchedule,
                monday,
                morning,
                workerB.getId()
        ));

        mockMvc.perform(post("/api/work-places/{workPlaceId}/work-change-requests/substitute", workPlace.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(workerA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "requestAssignmentId": %d,
                                  "targetMemberId": %d,
                                  "reason": "개인 일정으로 대타 요청합니다."
                                }
                                """.formatted(morningAssignmentA.getId(), workerB.getId())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("4005"));
    }

    /**
     * 근무자는 서로의 확정 근무를 교대 요청할 수 있다.
     */
    @Test
    void createShiftSwapRequest_success() throws Exception {
        mockMvc.perform(post("/api/work-places/{workPlaceId}/work-change-requests/shift-swap", workPlace.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(workerA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "requestAssignmentId": %d,
                                  "targetAssignmentId": %d,
                                  "reason": "오후 근무와 교대 요청합니다."
                                }
                                """.formatted(morningAssignmentA.getId(), afternoonAssignmentB.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.requestType").value("SHIFT_SWAP"))
                .andExpect(jsonPath("$.status").value("REQUESTED"))
                .andExpect(jsonPath("$.targetMemberId").value(workerB.getId()))
                .andExpect(jsonPath("$.targetAssignmentId").value(afternoonAssignmentB.getId()));
    }

    /**
     * 근무자는 자신이 보낸 요청과 받은 요청을 각각 조회할 수 있고, 사장은 사업장 전체 요청을 조회할 수 있다.
     */
    @Test
    void getRequestLists_success() throws Exception {
        createSubstituteRequest();

        mockMvc.perform(get("/api/work-places/{workPlaceId}/work-change-requests", workPlace.getId())
                        .param("scope", "SENT")
                        .header(HttpHeaders.AUTHORIZATION, bearer(workerA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].requesterMemberId").value(workerA.getId()));

        mockMvc.perform(get("/api/work-places/{workPlaceId}/work-change-requests", workPlace.getId())
                        .param("scope", "RECEIVED")
                        .header(HttpHeaders.AUTHORIZATION, bearer(workerB)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].targetMemberId").value(workerB.getId()));

        mockMvc.perform(get("/api/work-places/{workPlaceId}/owner/work-change-requests", workPlace.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].workPlaceId").value(workPlace.getId()));
    }

    private Long createSubstituteRequest() throws Exception {
        String responseBody = mockMvc.perform(post("/api/work-places/{workPlaceId}/work-change-requests/substitute", workPlace.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(workerA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "requestAssignmentId": %d,
                                  "targetMemberId": %d,
                                  "reason": "개인 일정으로 대타 요청합니다."
                                }
                                """.formatted(morningAssignmentA.getId(), workerB.getId())))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return Long.valueOf(responseBody.replaceAll(".*\"workChangeRequestId\":(\\d+).*", "$1"));
    }

    private String bearer(Member member) {
        return "Bearer " + jwtTokenProvider.createToken(member, TokenType.ACCESS, 1800);
    }
}
