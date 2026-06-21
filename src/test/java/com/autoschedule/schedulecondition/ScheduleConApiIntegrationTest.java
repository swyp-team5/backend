package com.autoschedule.schedulecondition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.autoschedule.auth.domain.TokenType;
import com.autoschedule.auth.jwt.JwtTokenProvider;
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
import java.time.LocalDate;
import java.time.LocalTime;
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
 * 스케줄 조건 생성 및 최근 조회 API가 명세에 맞는 응답과 영속 상태를 만드는지 검증한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
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
    private JdbcTemplate jdbcTemplate;

    private Member owner;
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
     * 정상 스케줄 조건 생성 요청 JSON을 반환한다.
     * - 월~목: groupingId=1, workChangeCount=0, timeDetail 1개
     * - 금~토: groupingId=2, workChangeCount=0, timeDetail 1개
     * - 일요일: groupingId=null, timeDetails=[]
     */
    private String buildValidRequest() {
        LocalDate monday = LocalDate.of(2025, 7, 7);
        return """
                {
                  "workPlaceOpenTime": "09:00:00",
                  "workPlaceCloseTime": "22:00:00",
                  "minPersonalWorkCount": 1,
                  "maxPersonalWorkCount": 5,
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
                buildDayJson("MONDAY",    monday,          1, 0),
                buildDayJson("TUESDAY",   monday.plusDays(1), 1, 0),
                buildDayJson("WEDNESDAY", monday.plusDays(2), 1, 0),
                buildDayJson("THURSDAY",  monday.plusDays(3), 1, 0),
                buildDayJson("FRIDAY",    monday.plusDays(4), 2, 0),
                buildDayJson("SATURDAY",  monday.plusDays(5), 2, 0),
                buildSundayDayJson()
        );
    }

    /**
     * workChangeCount=1인 요일이 있어 timeDetail이 2개인 요청 JSON을 반환한다.
     * 그룹 1의 대표 요일(월요일)에 workPartNo 2, 1 역순으로 넣어 정렬 검증에 사용한다.
     */
    private String buildRequestWithMultipleTimeDetails() {
        LocalDate monday = LocalDate.of(2025, 7, 7);
        return """
                {
                  "workPlaceOpenTime": "09:00:00",
                  "workPlaceCloseTime": "22:00:00",
                  "minPersonalWorkCount": 1,
                  "maxPersonalWorkCount": 5,
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
                    %s,
                    %s,
                    %s,
                    %s,
                    %s,
                    %s
                  ]
                }
                """.formatted(
                monday,
                buildDayJson("TUESDAY",   monday.plusDays(1), 1, 0),
                buildDayJson("WEDNESDAY", monday.plusDays(2), 1, 0),
                buildDayJson("THURSDAY",  monday.plusDays(3), 1, 0),
                buildDayJson("FRIDAY",    monday.plusDays(4), 2, 0),
                buildDayJson("SATURDAY",  monday.plusDays(5), 2, 0),
                buildSundayDayJson()
        );
    }

    /**
     * minPersonalWorkCount와 maxPersonalWorkCount만 변경한 요청 JSON을 반환한다.
     */
    private String buildRequestWithMinMax(int min, int max) {
        LocalDate monday = LocalDate.of(2025, 7, 7);
        return """
                {
                  "workPlaceOpenTime": "09:00:00",
                  "workPlaceCloseTime": "22:00:00",
                  "minPersonalWorkCount": %d,
                  "maxPersonalWorkCount": %d,
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
                min, max,
                buildDayJson("MONDAY",    monday,          1, 0),
                buildDayJson("TUESDAY",   monday.plusDays(1), 1, 0),
                buildDayJson("WEDNESDAY", monday.plusDays(2), 1, 0),
                buildDayJson("THURSDAY",  monday.plusDays(3), 1, 0),
                buildDayJson("FRIDAY",    monday.plusDays(4), 2, 0),
                buildDayJson("SATURDAY",  monday.plusDays(5), 2, 0),
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
     * groupingId=null인 일요일 요일 JSON 조각을 반환한다.
     */
    private String buildSundayDayJson() {
        LocalDate sunday = LocalDate.of(2025, 7, 13);
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
                """.formatted(sunday);
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
        jdbcTemplate.update("delete from time_detail");
        jdbcTemplate.update("delete from day");
        jdbcTemplate.update("delete from week_schedule");
        jdbcTemplate.update("delete from work_place");
        jdbcTemplate.update("delete from member");
    }
}
