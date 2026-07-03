package com.autoschedule.schedulecondition;

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
import com.autoschedule.schedulecondition.domain.Day;
import com.autoschedule.schedulecondition.domain.DayStatus;
import com.autoschedule.schedulecondition.domain.ScheduleDayName;
import com.autoschedule.schedulecondition.domain.TimeDetail;
import com.autoschedule.schedulecondition.domain.TimeDetailStatus;
import com.autoschedule.schedulecondition.domain.WeekSchedule;
import com.autoschedule.schedulecondition.domain.WeekScheduleStatus;
import com.autoschedule.schedulecondition.repository.DayRepository;
import com.autoschedule.schedulecondition.repository.TimeDetailRepository;
import com.autoschedule.schedulecondition.repository.WeekScheduleRepository;
import com.autoschedule.workplace.domain.WorkPlace;
import com.autoschedule.workplace.domain.WorkPlaceSize;
import com.autoschedule.workplace.repository.WorkPlaceRepository;
import com.jayway.jsonpath.JsonPath;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
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
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 스케줄 조건 생성 및 최근 조회 API가 명세에 맞는 응답과 영속 상태를 만드는지 검증한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class ScheduleConApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private WorkPlaceRepository workPlaceRepository;

    @Autowired
    private WeekScheduleRepository weekScheduleRepository;

    @Autowired
    private DayRepository dayRepository;

    @Autowired
    private TimeDetailRepository timeDetailRepository;

    @Autowired
    private CrewRepository crewRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Member owner;
    private Member worker;
    private WorkPlace workPlace;

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

        workPlace = workPlaceRepository.save(WorkPlace.create(
                owner.getId(),
                WorkPlaceSize.ONE_TO_FOUR,
                "테스트 가게",
                "서울시 강남구 테헤란로 1",
                null
        ));

        worker = memberRepository.save(Member.create(
                SocialProvider.GOOGLE,
                "worker-subject",
                "worker@test.com",
                "근무자",
                "01033334444",
                MemberRole.WORKER
        ));
        crewRepository.save(Crew.createWorker(worker, workPlace));
    }

    // ─────────────────────────────────────────────
    // 정상 케이스
    // ─────────────────────────────────────────────

    /**
     * 스케줄 조건 생성 요청이 성공하면 201을 반환하고 weekScheduleId를 응답에 포함한다.
     */
    @Test
    void createScheduleCondition_success() throws Exception {
        mockMvc.perform(post("/api/work-places/{workPlaceId}/schedule-conditions", workPlace.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buildValidRequest()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.weekScheduleId").isNumber())
                .andExpect(jsonPath("$.workPlaceId").value(workPlace.getId()))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    /**
     * 그룹이 없는 휴일은 timeDetails 필드를 생략해도 스케줄 조건 생성에 성공한다.
     */
    @Test
    void createScheduleCondition_succeedsWhenUngroupedHolidayOmitsTimeDetails() throws Exception {
        mockMvc.perform(post("/api/work-places/{workPlaceId}/schedule-conditions", workPlace.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buildRequestWithOmittedHolidayTimeDetails()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    /**
     * 그룹이 있는 영업일은 timeDetails 필드가 없으면 400으로 거절한다.
     */
    @Test
    void createScheduleCondition_failsWhenGroupedDayOmitsTimeDetails() throws Exception {
        mockMvc.perform(post("/api/work-places/{workPlaceId}/schedule-conditions", workPlace.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buildRequestWithOmittedGroupedDayTimeDetails()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("4000"));
    }

    /**
     * 스케줄 조건 생성 후 WeekSchedule 테이블에
     * workPlaceOpenTime, workPlaceCloseTime, minPersonalWorkCount, maxPersonalWorkCount가 저장된다.
     */
    @Test
    void createScheduleCondition_weekScheduleStoresCommonFields() throws Exception {
        mockMvc.perform(post("/api/work-places/{workPlaceId}/schedule-conditions", workPlace.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buildValidRequest()))
                .andExpect(status().isCreated());

        WeekSchedule saved = weekScheduleRepository
                .findFirstByWorkPlace_IdAndStatusAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(
                        workPlace.getId(), WeekScheduleStatus.ACTIVE)
                .orElseThrow();

        assertThat(saved.getWorkPlaceOpenTime()).isEqualTo(LocalTime.of(9, 0));
        assertThat(saved.getWorkPlaceCloseTime()).isEqualTo(LocalTime.of(22, 0));
        assertThat(saved.getMinPersonalWorkCount()).isEqualTo(1);
        assertThat(saved.getMaxPersonalWorkCount()).isEqualTo(5);
    }

    /**
     * 사장이 입력한 제출 마감일은 week_schedule.due_date에 DATE로 저장된다.
     */
    @Test
    void createScheduleCondition_storesRequestedDueDate() throws Exception {
        LocalDate dueDate = LocalDate.now()
                .with(TemporalAdjusters.next(DayOfWeek.MONDAY))
                .minusDays(1);

        mockMvc.perform(post("/api/work-places/{workPlaceId}/schedule-conditions", workPlace.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buildValidRequest(dueDate)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.dueDate").value(dueDate.toString()));

        WeekSchedule saved = weekScheduleRepository.findAll().get(0);
        assertThat(saved.getDueDate()).isEqualTo(dueDate);
    }

    /**
     * 사장이 스케줄 조건을 초기화하면 삭제 상태가 되고 같은 다음 주 조건을 다시 만들 수 있다.
     */
    @Test
    void deleteScheduleCondition_marksWeekScheduleDeleted() throws Exception {
        String response = mockMvc.perform(post("/api/work-places/{workPlaceId}/schedule-conditions", workPlace.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buildValidRequest()))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        Number weekScheduleIdValue = JsonPath.read(response, "$.weekScheduleId");
        Long weekScheduleId = weekScheduleIdValue.longValue();

        mockMvc.perform(delete("/api/work-places/{workPlaceId}/schedule-conditions/{weekScheduleId}",
                        workPlace.getId(), weekScheduleId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isNoContent());

        WeekSchedule deleted = weekScheduleRepository.findById(weekScheduleId).orElseThrow();
        assertThat(deleted.getStatus()).isEqualTo(WeekScheduleStatus.DELETED);
        assertThat(deleted.getDeletedAt()).isNotNull();

        mockMvc.perform(post("/api/work-places/{workPlaceId}/schedule-conditions", workPlace.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buildValidRequest()))
                .andExpect(status().isCreated());
    }

    /**
     * 스케줄 조건 생성 후 Day에는 요일별 값(dayName, date, groupingId 등)만 저장되고,
     * workPlaceOpenTime 등 공통 조건은 Day에 존재하지 않는다.
     */
    @Test
    void createScheduleCondition_dayStoresOnlyDaySpecificFields() throws Exception {
        mockMvc.perform(post("/api/work-places/{workPlaceId}/schedule-conditions", workPlace.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buildValidRequest()))
                .andExpect(status().isCreated());

        WeekSchedule weekSchedule = weekScheduleRepository
                .findFirstByWorkPlace_IdAndStatusAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(
                        workPlace.getId(), WeekScheduleStatus.ACTIVE)
                .orElseThrow();

        List<Day> days = dayRepository.findByWeekSchedule_IdAndStatusAndDeletedAtIsNullOrderByDateAscIdAsc(
                weekSchedule.getId(), DayStatus.ACTIVE);

        // 총 7일 저장
        assertThat(days).hasSize(7);
        // 요일 이름 정상 저장
        assertThat(days).extracting(Day::getDayName)
                .containsExactlyInAnyOrder(
                        ScheduleDayName.MONDAY, ScheduleDayName.TUESDAY,
                        ScheduleDayName.WEDNESDAY, ScheduleDayName.THURSDAY,
                        ScheduleDayName.FRIDAY, ScheduleDayName.SATURDAY,
                        ScheduleDayName.SUNDAY);
    }

    /**
     * 스케줄 조건 생성 후 TimeDetail이 Day와 정상적으로 연결되어 저장된다.
     */
    @Test
    void createScheduleCondition_timeDetailLinkedToDay() throws Exception {
        mockMvc.perform(post("/api/work-places/{workPlaceId}/schedule-conditions", workPlace.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buildValidRequest()))
                .andExpect(status().isCreated());

        WeekSchedule weekSchedule = weekScheduleRepository
                .findFirstByWorkPlace_IdAndStatusAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(
                        workPlace.getId(), WeekScheduleStatus.ACTIVE)
                .orElseThrow();

        List<Day> days = dayRepository.findByWeekSchedule_IdAndStatusAndDeletedAtIsNullOrderByDateAscIdAsc(
                weekSchedule.getId(), DayStatus.ACTIVE);

        // groupingId가 있는 요일(월~토, 6일)마다 timeDetail이 1개씩 저장됨
        long timeDetailCount = days.stream()
                .filter(d -> d.getGroupingId() != null)
                .mapToLong(d -> timeDetailRepository
                        .findByDay_IdAndStatusAndDeletedAtIsNullOrderByWorkPartNoAsc(
                                d.getId(), TimeDetailStatus.ACTIVE)
                        .size())
                .sum();

        assertThat(timeDetailCount).isEqualTo(6);
    }

    /**
     * 최근 스케줄 조건 조회 시 groupingId 기준으로 월~목은 그룹 1, 금~토는 그룹 2로 묶인다.
     */
    @Test
    void getLatestScheduleCondition_daysGroupedByGroupingId() throws Exception {
        mockMvc.perform(post("/api/work-places/{workPlaceId}/schedule-conditions", workPlace.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buildValidRequest()))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/work-places/{workPlaceId}/schedule-conditions/latest", workPlace.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.groups").isArray())
                .andExpect(jsonPath("$.groups.length()").value(2))
                // 그룹 1: 월~목 (4개 요일)
                .andExpect(jsonPath("$.groups[0].groupingId").value(1))
                .andExpect(jsonPath("$.groups[0].dayNames.length()").value(4))
                // 그룹 2: 금~토 (2개 요일)
                .andExpect(jsonPath("$.groups[1].groupingId").value(2))
                .andExpect(jsonPath("$.groups[1].dayNames.length()").value(2));
    }

    /**
     * 최근 스케줄 조건 조회 시 groupingId가 null인 일요일은 groups 응답에서 제외된다.
     */
    @Test
    void getLatestScheduleCondition_sundayWithNullGroupingIdExcludedFromGroups() throws Exception {
        mockMvc.perform(post("/api/work-places/{workPlaceId}/schedule-conditions", workPlace.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buildValidRequest()))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/work-places/{workPlaceId}/schedule-conditions/latest", workPlace.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isOk())
                // SUNDAY는 groups에 포함되지 않으므로 dayNames 전체에서 SUNDAY가 없어야 함
                .andExpect(jsonPath("$.groups[*].dayNames[?(@ == 'SUNDAY')]").isEmpty());
    }

    /**
     * 최근 스케줄 조건 조회 시 workPlaceOpenTime, workPlaceCloseTime,
     * minPersonalWorkCount, maxPersonalWorkCount는 WeekSchedule에서 가져온 값이다.
     */
    @Test
    void getLatestScheduleCondition_commonConditionsComefromWeekSchedule() throws Exception {
        mockMvc.perform(post("/api/work-places/{workPlaceId}/schedule-conditions", workPlace.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buildValidRequest()))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/work-places/{workPlaceId}/schedule-conditions/latest", workPlace.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.groups[0].workPlaceOpenTime").value("09:00:00"))
                .andExpect(jsonPath("$.groups[0].workPlaceCloseTime").value("22:00:00"))
                .andExpect(jsonPath("$.groups[0].minPersonalWorkCount").value(1))
                .andExpect(jsonPath("$.groups[0].maxPersonalWorkCount").value(5));
    }

    /**
     * 최근 스케줄 조건 조회 시 timeDetails는 workPartNo 오름차순으로 정렬된다.
     */
    @Test
    void getLatestScheduleCondition_timeDetailsSortedByWorkPartNoAsc() throws Exception {
        mockMvc.perform(post("/api/work-places/{workPlaceId}/schedule-conditions", workPlace.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buildRequestWithMultipleTimeDetails()))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/work-places/{workPlaceId}/schedule-conditions/latest", workPlace.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.groups[0].timeDetails[0].workPartNo").value(1))
                .andExpect(jsonPath("$.groups[0].timeDetails[1].workPartNo").value(2));
    }

    /**
     * 최근 스케줄 조건 조회 시 dayNames는 날짜 오름차순으로 정렬된다.
     */
    @Test
    void getLatestScheduleCondition_dayNamesSortedByDateAsc() throws Exception {
        mockMvc.perform(post("/api/work-places/{workPlaceId}/schedule-conditions", workPlace.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buildValidRequest()))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/work-places/{workPlaceId}/schedule-conditions/latest", workPlace.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isOk())
                // 그룹 1(월~목): MONDAY가 첫 번째
                .andExpect(jsonPath("$.groups[0].dayNames[0]").value("MONDAY"))
                .andExpect(jsonPath("$.groups[0].dayNames[1]").value("TUESDAY"))
                .andExpect(jsonPath("$.groups[0].dayNames[2]").value("WEDNESDAY"))
                .andExpect(jsonPath("$.groups[0].dayNames[3]").value("THURSDAY"))
                // 그룹 2(금~토): FRIDAY가 첫 번째
                .andExpect(jsonPath("$.groups[1].dayNames[0]").value("FRIDAY"))
                .andExpect(jsonPath("$.groups[1].dayNames[1]").value("SATURDAY"));
    }

    // ─────────────────────────────────────────────
    // 예외 케이스
    // ─────────────────────────────────────────────

    /**
     * days가 빈 배열이면 스케줄 조건 생성 요청이 실패한다.
     */
    @Test
    void createScheduleCondition_failsWhenDaysIsEmpty() throws Exception {
        mockMvc.perform(post("/api/work-places/{workPlaceId}/schedule-conditions", workPlace.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "workPlaceOpenTime": "09:00:00",
                                  "workPlaceCloseTime": "22:00:00",
                                  "minPersonalWorkCount": 1,
                                  "maxPersonalWorkCount": 5,
                                  "days": []
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("4000"));
    }

    /**
     * workPlaceOpenTime이 null이면 스케줄 조건 생성 요청이 실패한다.
     */
    @Test
    void createScheduleCondition_failsWhenWorkPlaceOpenTimeIsNull() throws Exception {
        mockMvc.perform(post("/api/work-places/{workPlaceId}/schedule-conditions", workPlace.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "workPlaceOpenTime": null,
                                  "workPlaceCloseTime": "22:00:00",
                                  "minPersonalWorkCount": 1,
                                  "maxPersonalWorkCount": 5,
                                  "days": [%s]
                                }
                                """.formatted(buildSundayDayJson())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("4000"))
                .andExpect(jsonPath("$.errors[?(@.field == 'workPlaceOpenTime')]").exists());
    }

    /**
     * workPlaceCloseTime이 null이면 스케줄 조건 생성 요청이 실패한다.
     */
    @Test
    void createScheduleCondition_failsWhenWorkPlaceCloseTimeIsNull() throws Exception {
        mockMvc.perform(post("/api/work-places/{workPlaceId}/schedule-conditions", workPlace.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "workPlaceOpenTime": "09:00:00",
                                  "workPlaceCloseTime": null,
                                  "minPersonalWorkCount": 1,
                                  "maxPersonalWorkCount": 5,
                                  "days": [%s]
                                }
                                """.formatted(buildSundayDayJson())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("4000"))
                .andExpect(jsonPath("$.errors[?(@.field == 'workPlaceCloseTime')]").exists());
    }

    /**
     * minPersonalWorkCount가 null이면 스케줄 조건 생성 요청이 실패한다.
     */
    @Test
    void createScheduleCondition_failsWhenMinPersonalWorkCountIsNull() throws Exception {
        mockMvc.perform(post("/api/work-places/{workPlaceId}/schedule-conditions", workPlace.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "workPlaceOpenTime": "09:00:00",
                                  "workPlaceCloseTime": "22:00:00",
                                  "minPersonalWorkCount": null,
                                  "maxPersonalWorkCount": 5,
                                  "days": [%s]
                                }
                                """.formatted(buildSundayDayJson())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("4000"))
                .andExpect(jsonPath("$.errors[?(@.field == 'minPersonalWorkCount')]").exists());
    }

    /**
     * maxPersonalWorkCount가 null이면 스케줄 조건 생성 요청이 실패한다.
     */
    @Test
    void createScheduleCondition_failsWhenMaxPersonalWorkCountIsNull() throws Exception {
        mockMvc.perform(post("/api/work-places/{workPlaceId}/schedule-conditions", workPlace.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "workPlaceOpenTime": "09:00:00",
                                  "workPlaceCloseTime": "22:00:00",
                                  "minPersonalWorkCount": 1,
                                  "maxPersonalWorkCount": null,
                                  "days": [%s]
                                }
                                """.formatted(buildSundayDayJson())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("4000"))
                .andExpect(jsonPath("$.errors[?(@.field == 'maxPersonalWorkCount')]").exists());
    }

    /**
     * maxPersonalWorkCount가 7을 초과하면 스케줄 조건 생성 요청이 실패한다.
     */
    @Test
    void createScheduleCondition_failsWhenMaxPersonalWorkCountExceedsSeven() throws Exception {
        mockMvc.perform(post("/api/work-places/{workPlaceId}/schedule-conditions", workPlace.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "workPlaceOpenTime": "09:00:00",
                                  "workPlaceCloseTime": "22:00:00",
                                  "minPersonalWorkCount": 1,
                                  "maxPersonalWorkCount": 8,
                                  "days": [%s]
                                }
                                """.formatted(buildSundayDayJson())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("4000"))
                .andExpect(jsonPath("$.errors[?(@.field == 'maxPersonalWorkCount')]").exists());
    }

    /**
     * 동일 사업장에 같은 주차 이름(다음주)의 스케줄 조건이 이미 존재하면 409를 반환한다.
     */
    @Test
    void createScheduleCondition_failsWhenNextWeekScheduleAlreadyExists() throws Exception {
        // 다음주 주차 이름을 서비스와 동일한 로직으로 계산
        LocalDate nextMonday = LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.MONDAY));
        int year = nextMonday.getYear();
        int month = nextMonday.getMonthValue();
        int weekOfMonth = nextMonday.get(java.time.temporal.WeekFields.of(java.util.Locale.KOREA).weekOfMonth());
        String nextWeekName = year + "년 " + month + "월 " + weekOfMonth + "주차";

        // 이미 같은 주차 스케줄 조건이 존재하는 상태를 만든다
        weekScheduleRepository.save(WeekSchedule.create(
                workPlace,
                nextWeekName,
                defaultDueDate(),
                LocalTime.of(9, 0),
                LocalTime.of(22, 0),
                1,
                5
        ));

        mockMvc.perform(post("/api/work-places/{workPlaceId}/schedule-conditions", workPlace.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buildValidRequest()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("4005"));
    }

    // ─────────────────────────────────────────────
    // 달력 조회
    // ─────────────────────────────────────────────

    /**
     * 근무자가 소속된 사업장의 달력 활성화 날짜를 조회하면 200과 날짜 목록을 반환한다.
     */
    @Test
    void getCalendarActivateDates_success() throws Exception {
        WeekSchedule weekSchedule = saveWeekScheduleInDb();
        LocalDate monday = LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.MONDAY));
        saveDayInDb(weekSchedule, ScheduleDayName.MONDAY, monday, 1);
        saveDayInDb(weekSchedule, ScheduleDayName.TUESDAY, monday.plusDays(1), 1);
        saveDayInDb(weekSchedule, ScheduleDayName.WEDNESDAY, monday.plusDays(2), 1);
        saveDayInDb(weekSchedule, ScheduleDayName.THURSDAY, monday.plusDays(3), 1);
        saveDayInDb(weekSchedule, ScheduleDayName.FRIDAY, monday.plusDays(4), 2);
        saveDayInDb(weekSchedule, ScheduleDayName.SATURDAY, monday.plusDays(5), null);
        saveDayInDb(weekSchedule, ScheduleDayName.SUNDAY, monday.plusDays(6), null);

        mockMvc.perform(get("/api/work-places/{workPlaceId}/schedule-conditions/calendar-activate",
                        workPlace.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(worker)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.weekScheduleId").isNumber())
                .andExpect(jsonPath("$.workPlaceId").value(workPlace.getId()))
                .andExpect(jsonPath("$.availableDates").isArray());
    }

    /**
     * @WorkerOnly 엔드포인트에 사장이 접근하면 403을 반환한다.
     */
    @Test
    void getCalendarActivateDates_failsWhenOwnerRequests() throws Exception {
        mockMvc.perform(get("/api/work-places/{workPlaceId}/schedule-conditions/calendar-activate",
                        workPlace.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isForbidden());
    }

    /**
     * 해당 사업장 크루가 아닌 근무자가 달력을 조회하면 403을 반환한다.
     */
    @Test
    void getCalendarActivateDates_failsWhenNotCrewMember() throws Exception {
        Member stranger = memberRepository.save(Member.create(
                SocialProvider.GOOGLE, "stranger-subject", "stranger@test.com",
                "외부인", "01099998888", MemberRole.WORKER
        ));

        mockMvc.perform(get("/api/work-places/{workPlaceId}/schedule-conditions/calendar-activate",
                        workPlace.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(stranger)))
                .andExpect(status().isForbidden());
    }

    // ─────────────────────────────────────────────
    // 타임 상세 조회
    // ─────────────────────────────────────────────

    /**
     * 근무자가 존재하는 날짜의 타임 상세 정보를 조회하면 200과 상세 목록을 반환한다.
     */
    @Test
    void getDayTimeDetails_success() throws Exception {
        WeekSchedule weekSchedule = saveWeekScheduleInDb();
        LocalDate monday = LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.MONDAY));
        Day day = saveDayInDb(weekSchedule, ScheduleDayName.MONDAY, monday, 1);
        saveTimeDetailInDb(day);

        mockMvc.perform(get("/api/work-places/{workPlaceId}/week-schedules/{weekScheduleId}/days/{date}/time-details",
                        workPlace.getId(), weekSchedule.getId(), monday)
                        .header(HttpHeaders.AUTHORIZATION, bearer(worker)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.weekScheduleId").value(weekSchedule.getId()))
                .andExpect(jsonPath("$.date").value(monday.toString()))
                .andExpect(jsonPath("$.dayName").value("MONDAY"))
                .andExpect(jsonPath("$.timeDetails").isArray())
                .andExpect(jsonPath("$.timeDetails.length()").value(1));
    }

    /**
     * 존재하지 않는 날짜로 타임 상세 조회를 하면 404를 반환한다.
     */
    @Test
    void getDayTimeDetails_failsWhenDateNotFound() throws Exception {
        WeekSchedule weekSchedule = saveWeekScheduleInDb();

        mockMvc.perform(get("/api/work-places/{workPlaceId}/week-schedules/{weekScheduleId}/days/{date}/time-details",
                        workPlace.getId(), weekSchedule.getId(), LocalDate.of(2099, 1, 1))
                        .header(HttpHeaders.AUTHORIZATION, bearer(worker)))
                .andExpect(status().isNotFound());
    }

    /**
     * @WorkerOnly 엔드포인트에 사장이 접근하면 403을 반환한다.
     */
    @Test
    void getDayTimeDetails_failsWhenOwnerRequests() throws Exception {
        WeekSchedule weekSchedule = saveWeekScheduleInDb();
        LocalDate monday = LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.MONDAY));

        mockMvc.perform(get("/api/work-places/{workPlaceId}/week-schedules/{weekScheduleId}/days/{date}/time-details",
                        workPlace.getId(), weekSchedule.getId(), monday)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isForbidden());
    }

    // ─────────────────────────────────────────────
    // 타 사업장 사용자 접근
    // ─────────────────────────────────────────────

    /**
     * 다른 사업장의 사장이 스케줄 조건 생성을 시도하면 403을 반환한다.
     */
    @Test
    void createScheduleCondition_failsWhenOtherOwnerRequests() throws Exception {
        Member otherOwner = memberRepository.save(Member.create(
                SocialProvider.GOOGLE, "other-owner-subject", "other@test.com",
                "타사장", "01077776666", MemberRole.OWNER
        ));
        workPlaceRepository.save(WorkPlace.create(
                otherOwner.getId(), WorkPlaceSize.ONE_TO_FOUR, "타가게", "부산시 해운대구 1", null
        ));

        mockMvc.perform(post("/api/work-places/{workPlaceId}/schedule-conditions", workPlace.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(otherOwner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buildValidRequest()))
                .andExpect(status().isForbidden());
    }

    /**
     * 다른 사업장 소속 근무자가 달력 조회를 시도하면 403을 반환한다.
     */
    @Test
    void getCalendarActivateDates_failsWhenOtherWorkPlaceWorkerRequests() throws Exception {
        Member otherOwner = memberRepository.save(Member.create(
                SocialProvider.GOOGLE, "other-owner2-subject", "other2@test.com",
                "타사장2", "01055554444", MemberRole.OWNER
        ));
        WorkPlace otherWorkPlace = workPlaceRepository.save(WorkPlace.create(
                otherOwner.getId(), WorkPlaceSize.ONE_TO_FOUR, "타가게2", "부산시 해운대구 2", null
        ));
        Member otherWorker = memberRepository.save(Member.create(
                SocialProvider.GOOGLE, "other-worker-subject", "otherworker@test.com",
                "타근무자", "01044443333", MemberRole.WORKER
        ));
        crewRepository.save(Crew.createWorker(otherWorker, otherWorkPlace));

        mockMvc.perform(get("/api/work-places/{workPlaceId}/schedule-conditions/calendar-activate",
                        workPlace.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(otherWorker)))
                .andExpect(status().isForbidden());
    }

    // ─────────────────────────────────────────────
    // 승인되지 않은 근무자 접근
    // ─────────────────────────────────────────────

    /**
     * joinStatus가 PENDING인 크루가 달력 조회를 시도하면 403을 반환한다.
     */
    @Test
    void getCalendarActivateDates_failsWhenCrewNotApproved() throws Exception {
        Member pendingWorker = memberRepository.save(Member.create(
                SocialProvider.GOOGLE, "pending-subject", "pending@test.com",
                "미승인근무자", "01022221111", MemberRole.WORKER
        ));
        jdbcTemplate.update(
                "insert into crew (member_id, work_place_id, join_status, crew_role, status, created_at, updated_at) "
                        + "values (?, ?, 'PENDING', 'WORKER', 'ACTIVE', now(), now())",
                pendingWorker.getId(), workPlace.getId()
        );

        mockMvc.perform(get("/api/work-places/{workPlaceId}/schedule-conditions/calendar-activate",
                        workPlace.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(pendingWorker)))
                .andExpect(status().isForbidden());
    }

    // ─────────────────────────────────────────────
    // 삭제된 부모 리소스 접근
    // ─────────────────────────────────────────────

    /**
     * 삭제된 사업장에 스케줄 조건 생성을 시도하면 404를 반환한다.
     *
     */
    @Test
    void createScheduleCondition_failsWhenWorkPlaceDeleted() throws Exception {
        jdbcTemplate.update(
                "update work_place set deleted_at = now() where work_place_id = ?",
                workPlace.getId()
        );

        mockMvc.perform(post("/api/work-places/{workPlaceId}/schedule-conditions", workPlace.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buildValidRequest()))
                .andExpect(status().isNotFound());
    }

    /**
     * 삭제된 주간 스케줄의 타임 상세 조회를 시도하면 404를 반환한다.
     */
    @Test
    void getDayTimeDetails_failsWhenWeekScheduleDeleted() throws Exception {
        WeekSchedule weekSchedule = saveWeekScheduleInDb();
        LocalDate monday = LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.MONDAY));
        saveDayInDb(weekSchedule, ScheduleDayName.MONDAY, monday, 1);

        jdbcTemplate.update(
                "update week_schedule set deleted_at = now() where week_schedule_id = ?",
                weekSchedule.getId()
        );

        mockMvc.perform(get("/api/work-places/{workPlaceId}/week-schedules/{weekScheduleId}/days/{date}/time-details",
                        workPlace.getId(), weekSchedule.getId(), monday)
                        .header(HttpHeaders.AUTHORIZATION, bearer(worker)))
                .andExpect(status().isNotFound());
    }

    // ─────────────────────────────────────────────
    // 날짜 개수 / 중복 / 요일 불일치
    // ─────────────────────────────────────────────

    /**
     * days가 6개이면 스케줄 조건 생성 요청이 실패한다.
     */
    @Test
    void createScheduleCondition_failsWhenDaysCountIsSix() throws Exception {
        LocalDate nextMonday = LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.MONDAY));
        mockMvc.perform(post("/api/work-places/{workPlaceId}/schedule-conditions", workPlace.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "workPlaceOpenTime": "09:00:00",
                                  "workPlaceCloseTime": "22:00:00",
                                  "minPersonalWorkCount": 1,
                                  "maxPersonalWorkCount": 5,
                                  "days": [%s, %s, %s, %s, %s, %s]
                                }
                                """.formatted(
                                buildDayJson("MONDAY",    nextMonday,                 1, 0),
                                buildDayJson("TUESDAY",   nextMonday.plusDays(1), 1, 0),
                                buildDayJson("WEDNESDAY", nextMonday.plusDays(2), 1, 0),
                                buildDayJson("THURSDAY",  nextMonday.plusDays(3), 1, 0),
                                buildDayJson("FRIDAY",    nextMonday.plusDays(4), 2, 0),
                                buildDayJson("SATURDAY",  nextMonday.plusDays(5), 2, 0)
                        )))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("4000"));
    }

    /**
     * days가 8개이면 스케줄 조건 생성 요청이 실패한다.
     */
    @Test
    void createScheduleCondition_failsWhenDaysCountIsEight() throws Exception {
        LocalDate nextMonday = LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.MONDAY));
        mockMvc.perform(post("/api/work-places/{workPlaceId}/schedule-conditions", workPlace.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "workPlaceOpenTime": "09:00:00",
                                  "workPlaceCloseTime": "22:00:00",
                                  "minPersonalWorkCount": 1,
                                  "maxPersonalWorkCount": 5,
                                  "days": [%s, %s, %s, %s, %s, %s, %s, %s]
                                }
                                """.formatted(
                                buildDayJson("MONDAY",    nextMonday,                 1, 0),
                                buildDayJson("TUESDAY",   nextMonday.plusDays(1), 1, 0),
                                buildDayJson("WEDNESDAY", nextMonday.plusDays(2), 1, 0),
                                buildDayJson("THURSDAY",  nextMonday.plusDays(3), 1, 0),
                                buildDayJson("FRIDAY",    nextMonday.plusDays(4), 2, 0),
                                buildDayJson("SATURDAY",  nextMonday.plusDays(5), 2, 0),
                                buildSundayDayJson(),
                                buildDayJson("MONDAY",    nextMonday,                 1, 0)
                        )))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("4000"));
    }

    /**
     * 동일한 날짜가 중복 포함되면 스케줄 조건 생성 요청이 실패한다.
     */
    @Test
    void createScheduleCondition_failsWhenDuplicateDate() throws Exception {
        LocalDate nextMonday = LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.MONDAY));
        mockMvc.perform(post("/api/work-places/{workPlaceId}/schedule-conditions", workPlace.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "workPlaceOpenTime": "09:00:00",
                                  "workPlaceCloseTime": "22:00:00",
                                  "minPersonalWorkCount": 1,
                                  "maxPersonalWorkCount": 5,
                                  "days": [%s, %s, %s, %s, %s, %s, %s]
                                }
                                """.formatted(
                                buildDayJson("MONDAY",    nextMonday,                 1, 0),
                                buildDayJson("TUESDAY",   nextMonday,                 1, 0),
                                buildDayJson("WEDNESDAY", nextMonday.plusDays(2), 1, 0),
                                buildDayJson("THURSDAY",  nextMonday.plusDays(3), 1, 0),
                                buildDayJson("FRIDAY",    nextMonday.plusDays(4), 2, 0),
                                buildDayJson("SATURDAY",  nextMonday.plusDays(5), 2, 0),
                                buildSundayDayJson()
                        )))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("4000"));
    }

    /**
     * dayName과 실제 날짜의 요일이 불일치하면 스케줄 조건 생성 요청이 실패한다.
     */
    @Test
    void createScheduleCondition_failsWhenDayNameMismatch() throws Exception {
        LocalDate nextMonday = LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.MONDAY));
        mockMvc.perform(post("/api/work-places/{workPlaceId}/schedule-conditions", workPlace.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "workPlaceOpenTime": "09:00:00",
                                  "workPlaceCloseTime": "22:00:00",
                                  "minPersonalWorkCount": 1,
                                  "maxPersonalWorkCount": 5,
                                  "days": [%s, %s, %s, %s, %s, %s, %s]
                                }
                                """.formatted(
                                buildDayJson("TUESDAY",   nextMonday,                 1, 0),
                                buildDayJson("MONDAY",    nextMonday.plusDays(1), 1, 0),
                                buildDayJson("WEDNESDAY", nextMonday.plusDays(2), 1, 0),
                                buildDayJson("THURSDAY",  nextMonday.plusDays(3), 1, 0),
                                buildDayJson("FRIDAY",    nextMonday.plusDays(4), 2, 0),
                                buildDayJson("SATURDAY",  nextMonday.plusDays(5), 2, 0),
                                buildSundayDayJson()
                        )))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("4000"));
    }

    // ─────────────────────────────────────────────
    // 그룹별 조건 불일치
    // ─────────────────────────────────────────────

    /**
     * 같은 그룹 내 요일들의 타임 상세 조건이 다르면 스케줄 조건 생성 요청이 실패한다.
     */
    @Test
    void createScheduleCondition_failsWhenGroupConditionMismatch() throws Exception {
        LocalDate nextMonday = LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.MONDAY));
        mockMvc.perform(post("/api/work-places/{workPlaceId}/schedule-conditions", workPlace.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "workPlaceOpenTime": "09:00:00",
                                  "workPlaceCloseTime": "22:00:00",
                                  "minPersonalWorkCount": 1,
                                  "maxPersonalWorkCount": 5,
                                  "days": [
                                    {
                                      "dayName": "MONDAY", "date": "%s", "groupingId": 1,
                                      "workChangeCount": 0, "holidayStatus": false, "selectLimitStatus": false,
                                      "timeDetails": [{"workPartNo": 1, "workerCount": 2, "startTime": "09:00:00", "closeTime": "17:00:00", "restTime": 0}]
                                    },
                                    {
                                      "dayName": "TUESDAY", "date": "%s", "groupingId": 1,
                                      "workChangeCount": 0, "holidayStatus": false, "selectLimitStatus": false,
                                      "timeDetails": [{"workPartNo": 1, "workerCount": 2, "startTime": "10:00:00", "closeTime": "18:00:00", "restTime": 0}]
                                    },
                                    %s, %s, %s, %s, %s
                                  ]
                                }
                                """.formatted(
                                nextMonday,
                                nextMonday.plusDays(1),
                                buildDayJson("WEDNESDAY", nextMonday.plusDays(2), 1, 0),
                                buildDayJson("THURSDAY",  nextMonday.plusDays(3), 1, 0),
                                buildDayJson("FRIDAY",    nextMonday.plusDays(4), 2, 0),
                                buildDayJson("SATURDAY",  nextMonday.plusDays(5), 2, 0),
                                buildSundayDayJson()
                        )))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("4000"));
    }

    // ─────────────────────────────────────────────
    // 타임 겹침
    // ─────────────────────────────────────────────

    /**
     * 같은 요일 내 교대 타임 시간이 겹치면 스케줄 조건 생성 요청이 실패한다.
     * 파트1(09:00~15:00)과 파트2(13:00~18:00)가 겹치는 케이스.
     */
    @Test
    void createScheduleCondition_failsWhenTimeDetailsOverlap() throws Exception {
        LocalDate nextMonday = LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.MONDAY));
        mockMvc.perform(post("/api/work-places/{workPlaceId}/schedule-conditions", workPlace.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "workPlaceOpenTime": "09:00:00",
                                  "workPlaceCloseTime": "22:00:00",
                                  "minPersonalWorkCount": 1,
                                  "maxPersonalWorkCount": 5,
                                  "days": [
                                    {
                                      "dayName": "MONDAY", "date": "%s", "groupingId": 1,
                                      "workChangeCount": 1, "holidayStatus": false, "selectLimitStatus": false,
                                      "timeDetails": [
                                        {"workPartNo": 1, "workerCount": 2, "startTime": "09:00:00", "closeTime": "15:00:00", "restTime": 0},
                                        {"workPartNo": 2, "workerCount": 2, "startTime": "13:00:00", "closeTime": "18:00:00", "restTime": 0}
                                      ]
                                    },
                                    %s, %s, %s, %s, %s, %s
                                  ]
                                }
                                """.formatted(
                                nextMonday,
                                buildDayJson("TUESDAY",   nextMonday.plusDays(1), 1, 0),
                                buildDayJson("WEDNESDAY", nextMonday.plusDays(2), 1, 0),
                                buildDayJson("THURSDAY",  nextMonday.plusDays(3), 1, 0),
                                buildDayJson("FRIDAY",    nextMonday.plusDays(4), 2, 0),
                                buildDayJson("SATURDAY",  nextMonday.plusDays(5), 2, 0),
                                buildSundayDayJson()
                        )))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("4000"));
    }

    // ─────────────────────────────────────────────
    // 동시 생성
    // ─────────────────────────────────────────────

    /**
     * 동일 사업장에 같은 주차 스케줄 조건을 동시에 2개 요청하면 하나만 성공(201)하고 나머지는 실패(409)한다.
     */
    @Test
    void createScheduleCondition_handlesMultipleConcurrentRequests() throws Exception {
        String request = buildValidRequest();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch latch = new CountDownLatch(2);
        List<Integer> statuses = new CopyOnWriteArrayList<>();

        for (int i = 0; i < 2; i++) {
            executor.submit(() -> {
                try {
                    int status = mockMvc.perform(
                                    post("/api/work-places/{workPlaceId}/schedule-conditions", workPlace.getId())
                                            .header(HttpHeaders.AUTHORIZATION, bearer(owner))
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

        assertThat(statuses).containsExactlyInAnyOrder(201, 409);
    }

    // ─────────────────────────────────────────────
    // 경계값 테스트
    // ─────────────────────────────────────────────

    /**
     * minPersonalWorkCount = 1은 허용된 최솟값으로 성공한다.
     */
    @Test
    void createScheduleCondition_succeedsWhenMinPersonalWorkCountIsOne() throws Exception {
        mockMvc.perform(post("/api/work-places/{workPlaceId}/schedule-conditions", workPlace.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buildRequestWithMinMax(1, 5)))
                .andExpect(status().isCreated());
    }

    /**
     * maxPersonalWorkCount = 7은 허용된 최댓값으로 성공한다.
     */
    @Test
    void createScheduleCondition_succeedsWhenMaxPersonalWorkCountIsSeven() throws Exception {
        mockMvc.perform(post("/api/work-places/{workPlaceId}/schedule-conditions", workPlace.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buildRequestWithMinMax(1, 7)))
                .andExpect(status().isCreated());
    }

    /**
     * maxPersonalWorkCount = 8은 허용 범위를 초과하여 실패한다.
     */
    @Test
    void createScheduleCondition_failsWhenMaxPersonalWorkCountIsEight() throws Exception {
        mockMvc.perform(post("/api/work-places/{workPlaceId}/schedule-conditions", workPlace.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buildRequestWithMinMax(1, 8)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("4000"));
    }

    /**
     * workChangeCount = 0은 허용된 최솟값으로 성공한다 (timeDetails 개수 = workChangeCount + 1 = 1).
     */
    @Test
    void createScheduleCondition_succeedsWhenWorkChangeCountIsZero() throws Exception {
        mockMvc.perform(post("/api/work-places/{workPlaceId}/schedule-conditions", workPlace.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buildValidRequest()))
                .andExpect(status().isCreated());
    }

    // ─────────────────────────────────────────────
    // 헬퍼 메서드
    // ─────────────────────────────────────────────

    /**
     * 다음 주 날짜를 기반으로 정상 스케줄 조건 생성 요청 JSON을 반환한다.
     * - 월~목: groupingId=1, workChangeCount=0, timeDetail 1개
     * - 금~토: groupingId=2, workChangeCount=0, timeDetail 1개
     * - 일요일: groupingId=null, timeDetails=[]
     */
    private String buildValidRequest() {
        return buildValidRequest(defaultDueDate());
    }

    private String buildValidRequest(LocalDate dueDate) {
        LocalDate nextMonday = LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.MONDAY));
        return """
                {
                  "workPlaceOpenTime": "09:00:00",
                  "workPlaceCloseTime": "22:00:00",
                  "minPersonalWorkCount": 1,
                  "maxPersonalWorkCount": 5,
                  "dueDate": "%s",
                  "days": [
                    %s,
                    %s,
                    %s,
                    %s,
                    %s,
                    %s,
                    %s
                  ]
                }
                """.formatted(
                dueDate,
                buildDayJson("MONDAY",    nextMonday,             1, 0),
                buildDayJson("TUESDAY",   nextMonday.plusDays(1), 1, 0),
                buildDayJson("WEDNESDAY", nextMonday.plusDays(2), 1, 0),
                buildDayJson("THURSDAY",  nextMonday.plusDays(3), 1, 0),
                buildDayJson("FRIDAY",    nextMonday.plusDays(4), 2, 0),
                buildDayJson("SATURDAY",  nextMonday.plusDays(5), 2, 0),
                buildSundayDayJson()
        );
    }

    private LocalDate defaultDueDate() {
        return LocalDate.now()
                .with(TemporalAdjusters.next(DayOfWeek.MONDAY))
                .minusDays(1);
    }

    /**
     * workChangeCount=1인 요일이 있어 timeDetail이 2개인 요청 JSON을 반환한다.
     * 그룹 1의 대표 요일(월요일)에 workPartNo 2, 1 역순으로 넣어 정렬 검증에 사용한다.
     */
    private String buildRequestWithMultipleTimeDetails() {
        LocalDate nextMonday = LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.MONDAY));
        return """
                {
                  "workPlaceOpenTime": "09:00:00",
                  "workPlaceCloseTime": "22:00:00",
                  "minPersonalWorkCount": 1,
                  "maxPersonalWorkCount": 5,
                  "dueDate": "%s",
                  "days": [
                    {
                      "dayName": "MONDAY",
                      "date": "%s",
                      "groupingId": 1,
                      "workChangeCount": 1,
                      "holidayStatus": false,
                      "selectLimitStatus": false,
                      "timeDetails": [
                        { "workPartNo": 2, "workerCount": 2, "startTime": "14:00:00", "closeTime": "18:00:00", "restTime": 0 },
                        { "workPartNo": 1, "workerCount": 2, "startTime": "09:00:00", "closeTime": "13:00:00", "restTime": 0 }
                      ]
                    },
                    {
                      "dayName": "TUESDAY",
                      "date": "%s",
                      "groupingId": 1,
                      "workChangeCount": 1,
                      "holidayStatus": false,
                      "selectLimitStatus": false,
                      "timeDetails": [
                        { "workPartNo": 2, "workerCount": 2, "startTime": "14:00:00", "closeTime": "18:00:00", "restTime": 0 },
                        { "workPartNo": 1, "workerCount": 2, "startTime": "09:00:00", "closeTime": "13:00:00", "restTime": 0 }
                      ]
                    },
                    {
                      "dayName": "WEDNESDAY",
                      "date": "%s",
                      "groupingId": 1,
                      "workChangeCount": 1,
                      "holidayStatus": false,
                      "selectLimitStatus": false,
                      "timeDetails": [
                        { "workPartNo": 2, "workerCount": 2, "startTime": "14:00:00", "closeTime": "18:00:00", "restTime": 0 },
                        { "workPartNo": 1, "workerCount": 2, "startTime": "09:00:00", "closeTime": "13:00:00", "restTime": 0 }
                      ]
                    },
                    {
                      "dayName": "THURSDAY",
                      "date": "%s",
                      "groupingId": 1,
                      "workChangeCount": 1,
                      "holidayStatus": false,
                      "selectLimitStatus": false,
                      "timeDetails": [
                        { "workPartNo": 2, "workerCount": 2, "startTime": "14:00:00", "closeTime": "18:00:00", "restTime": 0 },
                        { "workPartNo": 1, "workerCount": 2, "startTime": "09:00:00", "closeTime": "13:00:00", "restTime": 0 }
                      ]
                    },
                    %s,
                    %s,
                    %s
                  ]
                }
                """.formatted(
                defaultDueDate(),
                nextMonday,
                nextMonday.plusDays(1),
                nextMonday.plusDays(2),
                nextMonday.plusDays(3),
                buildDayJson("FRIDAY",    nextMonday.plusDays(4), 2, 0),
                buildDayJson("SATURDAY",  nextMonday.plusDays(5), 2, 0),
                buildSundayDayJson()
        );
    }

    /**
     * minPersonalWorkCount와 maxPersonalWorkCount만 변경한 요청 JSON을 반환한다.
     */
    private String buildRequestWithMinMax(int min, int max) {
        LocalDate nextMonday = LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.MONDAY));
        return """
                {
                  "workPlaceOpenTime": "09:00:00",
                  "workPlaceCloseTime": "22:00:00",
                  "minPersonalWorkCount": %d,
                  "maxPersonalWorkCount": %d,
                  "dueDate": "%s",
                  "days": [
                    %s,
                    %s,
                    %s,
                    %s,
                    %s,
                    %s,
                    %s
                  ]
                }
                """.formatted(
                min, max, defaultDueDate(),
                buildDayJson("MONDAY",    nextMonday,             1, 0),
                buildDayJson("TUESDAY",   nextMonday.plusDays(1), 1, 0),
                buildDayJson("WEDNESDAY", nextMonday.plusDays(2), 1, 0),
                buildDayJson("THURSDAY",  nextMonday.plusDays(3), 1, 0),
                buildDayJson("FRIDAY",    nextMonday.plusDays(4), 2, 0),
                buildDayJson("SATURDAY",  nextMonday.plusDays(5), 2, 0),
                buildSundayDayJson()
        );
    }

    /**
     * 그룹이 없는 일요일 휴일의 timeDetails 필드를 생략한 정상 요청 JSON을 반환한다.
     */
    private String buildRequestWithOmittedHolidayTimeDetails() {
        LocalDate nextMonday = LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.MONDAY));
        return """
                {
                  "workPlaceOpenTime": "09:00:00",
                  "workPlaceCloseTime": "22:00:00",
                  "minPersonalWorkCount": 1,
                  "maxPersonalWorkCount": 5,
                  "dueDate": "%s",
                  "days": [
                    %s,
                    %s,
                    %s,
                    %s,
                    %s,
                    %s,
                    {
                      "dayName": "SUNDAY",
                      "date": "%s",
                      "groupingId": null,
                      "workChangeCount": 0,
                      "holidayStatus": true,
                      "selectLimitStatus": true
                    }
                  ]
                }
                """.formatted(
                defaultDueDate(),
                buildDayJson("MONDAY", nextMonday, 1, 0),
                buildDayJson("TUESDAY", nextMonday.plusDays(1), 1, 0),
                buildDayJson("WEDNESDAY", nextMonday.plusDays(2), 1, 0),
                buildDayJson("THURSDAY", nextMonday.plusDays(3), 1, 0),
                buildDayJson("FRIDAY", nextMonday.plusDays(4), 2, 0),
                buildDayJson("SATURDAY", nextMonday.plusDays(5), 2, 0),
                nextMonday.plusDays(6)
        );
    }

    /**
     * 그룹이 있는 월요일 영업일의 timeDetails 필드를 생략한 비정상 요청 JSON을 반환한다.
     */
    private String buildRequestWithOmittedGroupedDayTimeDetails() {
        LocalDate nextMonday = LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.MONDAY));
        return """
                {
                  "workPlaceOpenTime": "09:00:00",
                  "workPlaceCloseTime": "22:00:00",
                  "minPersonalWorkCount": 1,
                  "maxPersonalWorkCount": 5,
                  "dueDate": "%s",
                  "days": [
                    {
                      "dayName": "MONDAY",
                      "date": "%s",
                      "groupingId": 1,
                      "workChangeCount": 0,
                      "holidayStatus": false,
                      "selectLimitStatus": false
                    },
                    %s,
                    %s,
                    %s,
                    %s,
                    %s,
                    %s
                  ]
                }
                """.formatted(
                defaultDueDate(),
                nextMonday,
                buildDayJson("TUESDAY", nextMonday.plusDays(1), 1, 0),
                buildDayJson("WEDNESDAY", nextMonday.plusDays(2), 1, 0),
                buildDayJson("THURSDAY", nextMonday.plusDays(3), 1, 0),
                buildDayJson("FRIDAY", nextMonday.plusDays(4), 2, 0),
                buildDayJson("SATURDAY", nextMonday.plusDays(5), 2, 0),
                buildSundayDayJson()
        );
    }

    /**
     * groupingId가 있는 요일 1개(workChangeCount=0, timeDetail 1개)의 JSON 조각을 반환한다.
     */
    private String buildDayJson(String dayName, LocalDate date, int groupingId, int workChangeCount) {
        return """
                {
                  "dayName": "%s",
                  "date": "%s",
                  "groupingId": %d,
                  "workChangeCount": %d,
                  "holidayStatus": false,
                  "selectLimitStatus": false,
                  "timeDetails": [
                    { "workPartNo": 1, "workerCount": 2, "startTime": "09:00:00", "closeTime": "17:00:00", "restTime": 60 }
                  ]
                }
                """.formatted(dayName, date, groupingId, workChangeCount);
    }

    /**
     * 다음 주 일요일 날짜로 groupingId=null인 일요일 JSON 조각을 반환한다.
     */
    private String buildSundayDayJson() {
        LocalDate nextSunday = LocalDate.now()
                .with(TemporalAdjusters.next(DayOfWeek.MONDAY))
                .plusDays(6);
        return """
                {
                  "dayName": "SUNDAY",
                  "date": "%s",
                  "groupingId": null,
                  "workChangeCount": 0,
                  "holidayStatus": true,
                  "selectLimitStatus": false,
                  "timeDetails": []
                }
                """.formatted(nextSunday);
    }

    private WeekSchedule saveWeekScheduleInDb() {
        return weekScheduleRepository.save(WeekSchedule.create(
                workPlace,
                "테스트 주차",
                defaultDueDate(),
                LocalTime.of(9, 0),
                LocalTime.of(22, 0),
                1,
                5
        ));
    }

    private Day saveDayInDb(WeekSchedule weekSchedule, ScheduleDayName dayName,
                            LocalDate date, Integer groupingId) {
        return dayRepository.save(Day.create(
                weekSchedule, dayName, date, groupingId, 0, false, false
        ));
    }

    private TimeDetail saveTimeDetailInDb(Day day) {
        return timeDetailRepository.save(TimeDetail.create(
                day, 1, "오픈", 2,
                LocalTime.of(9, 0), LocalTime.of(17, 0), 60
        ));
    }

    /**
     * 테스트용 Bearer 토큰을 생성한다.
     */
    private String bearer(Member member) {
        return "Bearer " + jwtTokenProvider.createToken(member, TokenType.ACCESS, 1800);
    }

    /**
     * 테스트 격리를 위해 스케줄 관련 테이블을 참조 순서에 맞게 삭제한다.
     */
    private void cleanupDatabase() {
        com.autoschedule.support.TestDatabaseCleaner.clean(jdbcTemplate);
    }
}
