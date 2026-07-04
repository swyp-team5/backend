package com.autoschedule.schedulelogic;

import com.autoschedule.schedule.generator.*;
import com.autoschedule.schedulecondition.domain.Day;
import com.autoschedule.schedulecondition.domain.ScheduleDayName;
import com.autoschedule.schedulecondition.domain.TimeDetail;
import com.autoschedule.schedulecondition.domain.WeekSchedule;
import com.autoschedule.schedulelogic.dto.ScheduleAssignmentDto;
import com.autoschedule.schedulelogic.dto.ScheduleResultDto;
import com.autoschedule.schedulelogic.service.ScheduleAlgorithmService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ScheduleLogicIntegrationTest {

    private ScheduleAlgorithmService service;

    @BeforeEach
    void setUp() {
        service = new ScheduleAlgorithmService();
    }

    // ===================== 헬퍼 =====================

    private Day normalDay() {
        Day day = mock(Day.class);
        when(day.isHolidayStatus()).thenReturn(false);
        when(day.isSelectLimitStatus()).thenReturn(false);
        when(day.getDayName()).thenReturn(ScheduleDayName.MONDAY);
        when(day.getDate()).thenReturn(LocalDate.of(2025, 1, 6));
        return day;
    }

    private Day holidayDay() {
        Day day = mock(Day.class);
        when(day.isHolidayStatus()).thenReturn(true);
        return day;
    }

    private Day selectLimitDay() {
        Day day = mock(Day.class);
        when(day.isHolidayStatus()).thenReturn(false);
        when(day.isSelectLimitStatus()).thenReturn(true);
        when(day.getDayName()).thenReturn(ScheduleDayName.SATURDAY);
        when(day.getDate()).thenReturn(LocalDate.of(2025, 1, 11));
        return day;
    }

    private TimeDetail td(Long id, int workerCount, Day day) {
        TimeDetail timeDetail = mock(TimeDetail.class);
        when(timeDetail.getId()).thenReturn(id);
        when(timeDetail.getWorkerCount()).thenReturn(workerCount);
        when(timeDetail.getDay()).thenReturn(day);
        when(timeDetail.getTimeName()).thenReturn("오픈");
        return timeDetail;
    }

    private Map<Long, String> memberMap(Long... ids) {
        Map<Long, String> map = new LinkedHashMap<>();
        for (Long id : ids) {
            map.put(id, "근무자" + id);
        }
        return map;
    }

    // ===================== 기본 케이스 =====================

    @Test
    @DisplayName("time_detail이 없으면 결과가 나오지 않는다")
    void emptyTimeDetails_withWorkers_returnsNoResult() {
        List<ScheduleResultDto> results = service.execute(
                List.of(), Map.of(), memberMap(1L, 2L), 1, 3
        );

        assertThat(results).isEmpty();
    }

    @Test
    @DisplayName("근무자가 없고 time_detail도 없으면 빈 스케줄 결과 1건이 반환된다")
    void noWorkersNoTimeDetails_returnsOneEmptyResult() {
        List<ScheduleResultDto> results = service.execute(
                List.of(), Map.of(), Map.of(), 0, 0
        );

        assertThat(results).hasSize(1);
        assertThat(results.get(0).score()).isEqualTo(0);
        assertThat(results.get(0).assignments()).isEmpty();
    }

    @Test
    @DisplayName("근무자 1명, time_detail 1개(workerCount=1)이면 결과 1건, score 100")
    void singleWorker_singleTimeDetail_returnsPerfectScore() {
        TimeDetail timeDetail = td(1L, 1, normalDay());

        List<ScheduleResultDto> results = service.execute(
                List.of(timeDetail), Map.of(), memberMap(1L), 1, 5
        );

        assertThat(results).hasSize(1);
        assertThat(results.get(0).score()).isEqualTo(100);

        ScheduleAssignmentDto assignment = results.get(0).assignments().get(0);
        assertThat(assignment.assignedWorkerCount()).isEqualTo(1);
        assertThat(assignment.isFulfilled()).isTrue();
        assertThat(assignment.assignedWorkers()).extracting("memberId").containsExactly(1L);
    }

    // ===================== 필수 조건 1: 모든 근무자 1회 이상 배정 =====================

    @Test
    @DisplayName("근무자 2명인데 time_detail이 1개(workerCount=1)이면 한 명은 배정 0 → 결과 없음")
    void twoWorkers_oneSlot_unassignedWorkerExists_returnsNoResult() {
        TimeDetail timeDetail = td(1L, 1, normalDay());

        List<ScheduleResultDto> results = service.execute(
                List.of(timeDetail), Map.of(), memberMap(1L, 2L), 1, 5
        );

        assertThat(results).isEmpty();
    }

    @Test
    @DisplayName("근무자 2명, time_detail 2개(각 workerCount=1)이면 각자 1번씩 배정되는 조합이 나온다")
    void twoWorkers_twoSlots_eachAssignedOnce_returnsTwoResults() {
        Day day = normalDay();
        TimeDetail td1 = td(1L, 1, day);
        TimeDetail td2 = td(2L, 1, day);

        List<ScheduleResultDto> results = service.execute(
                List.of(td1, td2), Map.of(), memberMap(1L, 2L), 1, 1
        );

        assertThat(results).hasSize(2);
        results.forEach(r -> {
            assertThat(r.score()).isEqualTo(100);
            r.assignments().forEach(a -> assertThat(a.assignedWorkerCount()).isEqualTo(1));
        });
    }

    // ===================== 필수 조건 2: min/max 범위 =====================

    @Test
    @DisplayName("minWorkCount=2인데 배정 횟수가 1이면 조건 2 실패 → 결과 없음")
    void belowMinWorkCount_returnsNoResult() {
        TimeDetail timeDetail = td(1L, 1, normalDay());

        List<ScheduleResultDto> results = service.execute(
                List.of(timeDetail), Map.of(), memberMap(1L), 2, 5
        );

        assertThat(results).isEmpty();
    }

    @Test
    @DisplayName("maxWorkCount=1이고 근무자 1명일 때 time_detail 2개 중 두 번째는 배정 불가 → 미충족으로 포함")
    void exceedsMaxWorkCount_secondSlotUnfulfilled() {
        Day day = normalDay();
        TimeDetail td1 = td(1L, 1, day);
        TimeDetail td2 = td(2L, 1, day);

        List<ScheduleResultDto> results = service.execute(
                List.of(td1, td2), Map.of(), memberMap(1L), 1, 1
        );

        assertThat(results).hasSize(1);

        ScheduleAssignmentDto td2Assignment = results.get(0).assignments().stream()
                .filter(a -> a.timeDetailId().equals(2L))
                .findFirst().orElseThrow();
        assertThat(td2Assignment.isFulfilled()).isFalse();
        assertThat(td2Assignment.assignedWorkerCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("모든 근무자의 배정 횟수가 min~max 범위 내에 있는 경우만 결과에 포함된다")
    void allWorkersWithinMinMaxRange_included() {
        Day day = normalDay();
        TimeDetail td1 = td(1L, 1, day);
        TimeDetail td2 = td(2L, 1, day);
        TimeDetail td3 = td(3L, 1, day);

        List<ScheduleResultDto> results = service.execute(
                List.of(td1, td2, td3), Map.of(), memberMap(1L, 2L), 1, 2
        );

        assertThat(results).isNotEmpty();
        results.forEach(result -> {
            Map<Long, Long> countPerWorker = new HashMap<>();
            result.assignments().forEach(a ->
                    a.assignedWorkers().forEach(w ->
                            countPerWorker.merge(w.memberId(), 1L, Long::sum)));
            countPerWorker.values().forEach(count ->
                    assertThat(count).isBetween(1L, 2L));
        });
    }

    // ===================== 점수 계산 =====================

    @Test
    @DisplayName("workerCount를 충족하지 못한 time_detail이 있으면 점수가 100 미만이다")
    void unfulfilledTimeDetail_scoreBelow100() {
        Day day = normalDay();
        // workerCount=2인데 근무자 2명 → 한 슬롯은 충족, 다른 슬롯은 근무자 부족
        TimeDetail td1 = td(1L, 2, day); // 2명 필요
        TimeDetail td2 = td(2L, 1, day); // 1명 필요

        // 근무자 2명, max=1 → td1에 2명 배정 불가(각자 max=1이므로 1명씩만 가능)
        // td1: 1명만 배정 → isFulfilled=false
        List<ScheduleResultDto> results = service.execute(
                List.of(td1, td2), Map.of(), memberMap(1L, 2L), 1, 1
        );

        assertThat(results).isNotEmpty();
        results.forEach(r -> assertThat(r.score()).isLessThan(100));
    }

    @Test
    @DisplayName("모든 time_detail workerCount를 충족하면 score=100")
    void allFulfilled_score100() {
        Day day = normalDay();
        TimeDetail td1 = td(1L, 1, day);
        TimeDetail td2 = td(2L, 1, day);

        List<ScheduleResultDto> results = service.execute(
                List.of(td1, td2), Map.of(), memberMap(1L, 2L), 1, 2
        );

        assertThat(results).isNotEmpty();
        assertThat(results.get(0).score()).isEqualTo(100);
    }

    @Test
    @DisplayName("결과는 점수 내림차순으로 정렬된다")
    void resultsOrderedByScoreDescending() {
        Day day = normalDay();
        TimeDetail td1 = td(1L, 2, day);
        TimeDetail td2 = td(2L, 1, day);

        List<ScheduleResultDto> results = service.execute(
                List.of(td1, td2), Map.of(), memberMap(1L, 2L), 1, 3
        );

        assertThat(results).isNotEmpty();
        for (int i = 0; i < results.size() - 1; i++) {
            assertThat(results.get(i).score())
                    .isGreaterThanOrEqualTo(results.get(i + 1).score());
        }
    }

    @Test
    @DisplayName("점수가 같으면 배정 분산이 작은 결과가 앞에 온다")
    void sameScore_lowerVarianceFirst() {
        Day day = normalDay();
        TimeDetail td1 = td(1L, 1, day);
        TimeDetail td2 = td(2L, 1, day);
        TimeDetail td3 = td(3L, 1, day);

        List<ScheduleResultDto> results = service.execute(
                List.of(td1, td2, td3), Map.of(), memberMap(1L, 2L), 1, 3
        );

        assertThat(results).isNotEmpty();
        for (int i = 0; i < results.size() - 1; i++) {
            if (results.get(i).score().equals(results.get(i + 1).score())) {
                assertThat(results.get(i).assignmentVariance())
                        .isLessThanOrEqualTo(results.get(i + 1).assignmentVariance());
            }
        }
    }

    // ===================== 휴일 처리 =====================

    @Test
    @DisplayName("휴일 day의 time_detail은 배정되지 않고 점수 계산에서도 제외된다")
    void holidayTimeDetail_skippedAndExcludedFromScore() {
        Day holiday = holidayDay();
        Day normal = normalDay();
        TimeDetail holidayTd = td(1L, 1, holiday);
        TimeDetail normalTd = td(2L, 1, normal);

        List<ScheduleResultDto> results = service.execute(
                List.of(holidayTd, normalTd), Map.of(), memberMap(1L), 1, 5
        );

        assertThat(results).hasSize(1);
        assertThat(results.get(0).assignments()).hasSize(1);
        assertThat(results.get(0).assignments().get(0).timeDetailId()).isEqualTo(2L);
        assertThat(results.get(0).score()).isEqualTo(100);
    }

    @Test
    @DisplayName("모든 time_detail이 휴일이면 결과가 나오지 않는다")
    void allHolidays_returnsNoResult() {
        Day holiday = holidayDay();
        TimeDetail td1 = td(1L, 1, holiday);
        TimeDetail td2 = td(2L, 1, holiday);

        List<ScheduleResultDto> results = service.execute(
                List.of(td1, td2), Map.of(), memberMap(1L), 0, 5
        );

        assertThat(results).isEmpty();
    }

    // ===================== 불가 time_detail (unavailableMap) =====================

    @Test
    @DisplayName("불가 time_detail에 등록된 근무자는 해당 시간에 배정되지 않는다")
    void unavailableWorker_notAssignedToBlockedTimeDetail() {
        TimeDetail timeDetail = td(1L, 1, normalDay());
        Map<Long, Set<Long>> unavailableMap = Map.of(1L, Set.of(1L));

        List<ScheduleResultDto> results = service.execute(
                List.of(timeDetail), unavailableMap, memberMap(1L), 1, 5
        );

        assertThat(results).isEmpty();
    }

    @Test
    @DisplayName("불가 time_detail이 있는 근무자는 다른 time_detail에는 배정될 수 있다")
    void unavailableWorker_assignedToOtherTimeDetails() {
        Day day = normalDay();
        TimeDetail td1 = td(1L, 1, day);
        TimeDetail td2 = td(2L, 1, day);
        Map<Long, Set<Long>> unavailableMap = Map.of(1L, Set.of(1L));

        List<ScheduleResultDto> results = service.execute(
                List.of(td1, td2), unavailableMap, memberMap(1L, 2L), 1, 2
        );

        assertThat(results).isNotEmpty();
        results.forEach(result -> {
            ScheduleAssignmentDto td1Assignment = result.assignments().stream()
                    .filter(a -> a.timeDetailId().equals(1L))
                    .findFirst().orElseThrow();
            boolean worker1AssignedToTd1 = td1Assignment.assignedWorkers().stream()
                    .anyMatch(w -> w.memberId().equals(1L));
            assertThat(worker1AssignedToTd1).isFalse();
        });
    }

    @Test
    @DisplayName("모든 근무자가 해당 time_detail에 불가이면 결과가 나오지 않는다")
    void allWorkersUnavailable_returnsNoResult() {
        TimeDetail timeDetail = td(1L, 1, normalDay());
        Map<Long, Set<Long>> unavailableMap = Map.of(
                1L, Set.of(1L),
                2L, Set.of(1L)
        );

        List<ScheduleResultDto> results = service.execute(
                List.of(timeDetail), unavailableMap, memberMap(1L, 2L), 1, 5
        );

        assertThat(results).isEmpty();
    }

    // ===================== 선택제한 처리 =====================

    @Test
    @DisplayName("선택제한 day의 time_detail은 불가 근무자를 제외하고 배정된다")
    void selectLimitDay_unavailableWorkerNotAssigned() {
        Day normal = normalDay();
        Day selectLimit = selectLimitDay();

        // 일반 슬롯 2개 추가해서 두 근무자 모두 1회 이상 배정 가능하게 설정
        TimeDetail normalTd1 = td(2L, 1, normal);
        TimeDetail normalTd2 = td(3L, 1, normal);
        TimeDetail selectTd = td(1L, 1, selectLimit);

        Map<Long, Set<Long>> unavailableMap = Map.of(1L, Set.of(1L));

        List<ScheduleResultDto> results = service.execute(
                List.of(selectTd, normalTd1, normalTd2),
                unavailableMap,
                memberMap(1L, 2L),
                1, 3
        );

        assertThat(results).isNotEmpty();
        // 선택제한 슬롯에 불가 근무자(1L)가 배정되지 않았는지 확인
        results.forEach(result -> {
            ScheduleAssignmentDto selectAssignment = result.assignments().stream()
                    .filter(a -> a.timeDetailId().equals(1L))
                    .findFirst().orElseThrow();
            boolean worker1Assigned = selectAssignment.assignedWorkers().stream()
                    .anyMatch(w -> w.memberId().equals(1L));
            assertThat(worker1Assigned).isFalse();
        });
    }

    // ===================== 결과 개수 상한 =====================

    @Test
    @DisplayName("경우의 수가 100개를 초과하더라도 최대 100건만 반환한다")
    void manyResults_cappedAt100() {
        Day day = normalDay();
        List<TimeDetail> timeDetails = new ArrayList<>();
        for (long i = 1; i <= 10; i++) {
            timeDetails.add(td(i, 1, day));
        }

        Map<Long, String> members = new LinkedHashMap<>();
        for (long i = 1; i <= 8; i++) {
            members.put(i, "근무자" + i);
        }

        List<ScheduleResultDto> results = service.execute(
                timeDetails, Map.of(), members, 1, 3
        );

        assertThat(results.size()).isLessThanOrEqualTo(100);
    }

    // ===================== 복합 케이스 =====================

    @Test
    @DisplayName("isFulfilled는 assignedWorkerCount >= requiredWorkerCount일 때 true다")
    void isFulfilled_reflectsWorkerCountSatisfaction() {
        Day day = normalDay();
        TimeDetail td1 = td(1L, 2, day);
        TimeDetail td2 = td(2L, 1, day);

        List<ScheduleResultDto> results = service.execute(
                List.of(td1, td2), Map.of(), memberMap(1L, 2L), 1, 3
        );

        assertThat(results).isNotEmpty();
        results.forEach(result ->
                result.assignments().forEach(a -> {
                    boolean expectedFulfilled = a.assignedWorkerCount() >= a.requiredWorkerCount();
                    assertThat(a.isFulfilled()).isEqualTo(expectedFulfilled);
                }));
    }

    @Test
    @DisplayName("배정된 근무자 이름은 memberNameMap에서 정확히 조회된다")
    void assignedWorkerName_matchesMemberNameMap() {
        TimeDetail timeDetail = td(1L, 1, normalDay());

        List<ScheduleResultDto> results = service.execute(
                List.of(timeDetail), Map.of(), memberMap(1L), 1, 5
        );

        assertThat(results).hasSize(1);
        assertThat(results.get(0).assignments().get(0).assignedWorkers())
                .extracting("memberName")
                .containsExactly("근무자1");
    }

    @Test
    @DisplayName("같은 time_detail에 동일 근무자가 중복 배정되지 않는다")
    void noDuplicateAssignmentPerTimeDetail() {
        TimeDetail timeDetail = td(1L, 3, normalDay());

        List<ScheduleResultDto> results = service.execute(
                List.of(timeDetail), Map.of(), memberMap(1L, 2L, 3L), 1, 5
        );

        assertThat(results).isNotEmpty();
        results.forEach(result ->
                result.assignments().forEach(a -> {
                    List<Long> workerIds = a.assignedWorkers().stream()
                            .map(w -> w.memberId())
                            .toList();
                    assertThat(workerIds).doesNotHaveDuplicates();
                }));
    }

    @Test
    @DisplayName("월~일 7일치 스케줄에서 휴일/선택제한/일반 배정이 각각 올바르게 처리된다")
    void fullWeek_holidayAndSelectLimitAndNormal_processedCorrectly() {
        // 월: 휴일
        Day monday = mock(Day.class);
        when(monday.isHolidayStatus()).thenReturn(true);

        // 화: 선택제한
        Day tuesday = mock(Day.class);
        when(tuesday.isHolidayStatus()).thenReturn(false);
        when(tuesday.isSelectLimitStatus()).thenReturn(true);
        when(tuesday.getDayName()).thenReturn(ScheduleDayName.TUESDAY);
        when(tuesday.getDate()).thenReturn(LocalDate.of(2025, 1, 7));

        // 수~일: 일반
        Day wednesday = mock(Day.class);
        when(wednesday.isHolidayStatus()).thenReturn(false);
        when(wednesday.isSelectLimitStatus()).thenReturn(false);
        when(wednesday.getDayName()).thenReturn(ScheduleDayName.WEDNESDAY);
        when(wednesday.getDate()).thenReturn(LocalDate.of(2025, 1, 8));

        Day thursday = mock(Day.class);
        when(thursday.isHolidayStatus()).thenReturn(false);
        when(thursday.isSelectLimitStatus()).thenReturn(false);
        when(thursday.getDayName()).thenReturn(ScheduleDayName.THURSDAY);
        when(thursday.getDate()).thenReturn(LocalDate.of(2025, 1, 9));

        Day friday = mock(Day.class);
        when(friday.isHolidayStatus()).thenReturn(false);
        when(friday.isSelectLimitStatus()).thenReturn(false);
        when(friday.getDayName()).thenReturn(ScheduleDayName.FRIDAY);
        when(friday.getDate()).thenReturn(LocalDate.of(2025, 1, 10));

        Day saturday = mock(Day.class);
        when(saturday.isHolidayStatus()).thenReturn(false);
        when(saturday.isSelectLimitStatus()).thenReturn(false);
        when(saturday.getDayName()).thenReturn(ScheduleDayName.SATURDAY);
        when(saturday.getDate()).thenReturn(LocalDate.of(2025, 1, 11));

        Day sunday = mock(Day.class);
        when(sunday.isHolidayStatus()).thenReturn(false);
        when(sunday.isSelectLimitStatus()).thenReturn(false);
        when(sunday.getDayName()).thenReturn(ScheduleDayName.SUNDAY);
        when(sunday.getDate()).thenReturn(LocalDate.of(2025, 1, 12));

        // 각 요일마다 time_detail 3개씩 (workerCount=1)
        List<TimeDetail> week = List.of(
                td(1L,  1, monday),    td(2L,  1, monday),    td(3L,  1, monday),    // 휴일
                td(4L,  1, tuesday),   td(5L,  1, tuesday),   td(6L,  1, tuesday),   // 선택제한
                td(7L,  2, wednesday), td(8L,  2, wednesday), td(9L,  2, wednesday), // 일반
                td(10L, 1, thursday),  td(11L, 1, thursday),  td(12L, 1, thursday),  // 일반
                td(13L, 2, friday),    td(14L, 2, friday),    td(15L, 2, friday),    // 일반
                td(16L, 2, saturday),  td(17L, 2, saturday),  td(18L, 2, saturday),  // 일반
                td(19L, 2, sunday),    td(20L, 2, sunday),    td(21L, 2, sunday)     // 일반
        );

        Map<Long, String> members = memberMap(1L, 2L, 3L, 4L, 5L);

        List<ScheduleResultDto> results = service.execute(week, Map.of(), members, 1, 5);

        // ── 결과 출력 ──────────────────────────────────────────
        System.out.println("=================================================");
        System.out.println("총 경우의 수: " + results.size() + "개");
        System.out.println("=================================================");

        for (int i = 0; i < results.size(); i++) {
            ScheduleResultDto result = results.get(i);
            System.out.printf("%n[스케줄 %d] score=%d, variance=%.2f%n",
                    i + 1, result.score(), result.assignmentVariance());
            System.out.println("-------------------------------------------------");

            for (ScheduleAssignmentDto a : result.assignments()) {
                String workers = a.assignedWorkers().isEmpty()
                        ? "배정 없음"
                        : a.assignedWorkers().stream()
                        .map(w -> w.memberName())
                        .collect(java.util.stream.Collectors.joining(", "));
                System.out.printf("  time_detail[%2d] | %s %s | 필요:%d명 배정:%d명 | 충족:%s | 배정근무자: %s%n",
                        a.timeDetailId(),
                        a.dayName(),
                        a.date(),
                        a.requiredWorkerCount(),
                        a.assignedWorkerCount(),
                        a.isFulfilled() ? "O" : "X",
                        workers
                );
            }
        }
        System.out.println("=================================================");
        // ──────────────────────────────────────────────────────

        assertThat(results).isNotEmpty();

        results.forEach(result -> {
            List<ScheduleAssignmentDto> assignments = result.assignments();

            // 휴일(월요일) time_detail 3개는 assignments에 포함되지 않음
            boolean hasMonday = assignments.stream()
                    .anyMatch(a -> a.timeDetailId() <= 3L);
            assertThat(hasMonday).isFalse();

            // 휴일 제외 18개 time_detail만 assignments에 포함
            assertThat(assignments).hasSize(18);

            // 모든 assignments의 isFulfilled는 assignedWorkerCount >= requiredWorkerCount와 일치
            assignments.forEach(a -> {
                boolean expectedFulfilled = a.assignedWorkerCount() >= a.requiredWorkerCount();
                assertThat(a.isFulfilled()).isEqualTo(expectedFulfilled);
            });

            // 점수는 0~100 범위 내
            assertThat(result.score()).isBetween(0, 100);

            // 분산은 0 이상
            assertThat(result.assignmentVariance()).isGreaterThanOrEqualTo(0.0);
        });
    }

    @Test
    @DisplayName("월~일 7일치 스케줄에서 ExhaustivePruningScheduleCandidateGenerator가 올바르게 동작한다")
    void fullWeek_exhaustivePruning_processedCorrectly() {
        WeekSchedule weekSchedule = WeekSchedule.create(
                null,
                "테스트 주간",
                LocalDate.of(2025, 1, 6),
                LocalTime.of(9, 0),
                LocalTime.of(22, 0),
                2,
                2
        );

        Day wednesday = Day.create(weekSchedule, ScheduleDayName.WEDNESDAY, LocalDate.of(2025, 1, 8), 1, 1, false, false);
        ReflectionTestUtils.setField(wednesday, "id", 1L);

        Day thursday = Day.create(weekSchedule, ScheduleDayName.THURSDAY, LocalDate.of(2025, 1, 9), 1, 1, false, false);
        ReflectionTestUtils.setField(thursday, "id", 2L);

        Day friday = Day.create(weekSchedule, ScheduleDayName.FRIDAY, LocalDate.of(2025, 1, 10), 1, 1, false, false);
        ReflectionTestUtils.setField(friday, "id", 3L);

        // 3요일 × 2슬롯 = 6슬롯, 근무자 3명 * min(2) = 6 → 가지치기 강력하게 동작
        List<TimeDetail> timeDetails = List.of(
                td(1L, 1, wednesday), td(2L, 1, wednesday),
                td(3L, 1, thursday),  td(4L, 1, thursday),
                td(5L, 1, friday),    td(6L, 1, friday)
        );

        List<Long> workerMemberIds = List.of(1L, 2L, 3L);

        Map<Long, String> memberNameMap = new LinkedHashMap<>();
        memberNameMap.put(1L, "근무자1");
        memberNameMap.put(2L, "근무자2");
        memberNameMap.put(3L, "근무자3");

        ScheduleCandidateGenerationCommand command = new ScheduleCandidateGenerationCommand(
                weekSchedule,
                timeDetails,
                workerMemberIds,
                Map.of()
        );

        ExhaustivePruningScheduleCandidateGenerator generator =
                new ExhaustivePruningScheduleCandidateGenerator();

        ScheduleCandidateGenerationResult result = generator.generate(command);

        // ── 결과 출력 ──────────────────────────────────────────
        System.out.println("=================================================");
        System.out.println("알고리즘 버전: " + result.algorithmVersion());
        System.out.println("총 경우의 수: " + result.candidates().size() + "개");
        System.out.println("=================================================");

        for (ScheduleCandidate candidate : result.candidates()) {
            System.out.printf("%n[스케줄 %d] score=%d%n",
                    candidate.candidateNo(), candidate.score());
            System.out.println("-------------------------------------------------");

            for (ScheduleCandidateDay day : candidate.days()) {
                System.out.printf("  Day[%d]%n", day.dayId());
                for (ScheduleCandidateTimeDetail detail : day.timeDetails()) {
                    String workers = detail.workerMemberIds().stream()
                            .map(id -> memberNameMap.getOrDefault(id, "알수없음"))
                            .collect(java.util.stream.Collectors.joining(", "));
                    System.out.printf("    time_detail[%2d] | 배정근무자: %s%n",
                            detail.timeDetailId(), workers);
                }
            }
        }
        System.out.println("=================================================");
        // ──────────────────────────────────────────────────────

        assertThat(result.algorithmVersion()).isEqualTo("BIT_DFS_MRV_TOPN_V1");
        assertThat(result.candidates()).isNotEmpty();
        assertThat(result.candidates().size()).isLessThanOrEqualTo(50);

        // candidateNo는 1부터 순서대로
        for (int i = 0; i < result.candidates().size(); i++) {
            assertThat(result.candidates().get(i).candidateNo()).isEqualTo(i + 1);
        }

        // 점수 내림차순 정렬
        List<ScheduleCandidate> candidates = result.candidates();
        for (int i = 0; i < candidates.size() - 1; i++) {
            assertThat(candidates.get(i).score())
                    .isGreaterThanOrEqualTo(candidates.get(i + 1).score());
        }

        // 각 후보는 3개 요일로 구성
        result.candidates().forEach(candidate ->
                assertThat(candidate.days()).isNotEmpty()
        );

        // 각 후보에서 근무자별 배정 횟수는 정확히 2
        result.candidates().forEach(candidate -> {
            Map<Long, Long> countPerWorker = new HashMap<>();
            candidate.days().forEach(day ->
                    day.timeDetails().forEach(td ->
                            td.workerMemberIds().forEach(workerId ->
                                    countPerWorker.merge(workerId, 1L, Long::sum)
                            )
                    )
            );
            countPerWorker.values().forEach(count ->
                    assertThat(count).isEqualTo(2L)
            );
        });
    }
}