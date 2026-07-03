package com.autoschedule.schedule.generator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import com.autoschedule.schedulecondition.domain.Day;
import com.autoschedule.schedulecondition.domain.ScheduleDayName;
import com.autoschedule.schedulecondition.domain.TimeDetail;
import com.autoschedule.schedulecondition.domain.WeekSchedule;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class ExhaustivePruningScheduleCandidateGeneratorTest {

    private static final String EXPECTED_ALGORITHM_VERSION = "BIT_DFS_MRV_TOPN_V1";
    private static final int MAX_CANDIDATE_COUNT = 50;

    private final ExhaustivePruningScheduleCandidateGenerator generator =
        new ExhaustivePruningScheduleCandidateGenerator();

    /**
     * 가능한 후보가 폭발하는 입력에서도 최대 후보 수까지만 반환한다.
     */
    @Test
    void generate_limitsCandidateCountWhenSearchSpaceIsLarge() {
        WeekSchedule weekSchedule = createWeekSchedule(0, 10);
        Day day = createDay(weekSchedule, 1L);

        List<TimeDetail> timeDetails = List.of(
            createTimeDetail(day, 1L, 1),
            createTimeDetail(day, 2L, 2),
            createTimeDetail(day, 3L, 3),
            createTimeDetail(day, 4L, 4)
        );

        ScheduleCandidateGenerationResult result = measure("candidate-limit", () ->
            generator.generate(new ScheduleCandidateGenerationCommand(
                weekSchedule,
                timeDetails,
                List.of(1L, 2L, 3L, 4L, 5L),
                Map.of()
            ))
        );

        assertThat(result.algorithmVersion()).isEqualTo(EXPECTED_ALGORITHM_VERSION);
        assertThat(result.candidates()).hasSize(MAX_CANDIDATE_COUNT);
        assertCandidateNumbersAreSequential(result);
    }

    /**
     * 내부 탐색 순서를 바꿔도 후보 응답은 원래 time_detail 순서를 유지한다.
     */
    @Test
    void generate_preservesOriginalTimeDetailOrderInCandidateResponse() {
        WeekSchedule weekSchedule = createWeekSchedule(0, 10);
        Day day = createDay(weekSchedule, 1L);

        TimeDetail broadSlot = createTimeDetail(day, 1L, 1, 1);
        TimeDetail restrictiveSlot = createTimeDetail(day, 2L, 2, 1);

        ScheduleCandidateGenerationResult result = measure("order-preserved", () ->
            generator.generate(new ScheduleCandidateGenerationCommand(
                weekSchedule,
                List.of(broadSlot, restrictiveSlot),
                List.of(1L, 2L, 3L),
                Map.of(restrictiveSlot.getId(), Set.of(1L, 2L))
            ))
        );

        assertThat(result.algorithmVersion()).isEqualTo(EXPECTED_ALGORITHM_VERSION);
        assertThat(result.candidates()).isNotEmpty();

        assertThat(result.candidates().get(0).days().get(0).timeDetails())
            .extracting(ScheduleCandidateTimeDetail::timeDetailId)
            .containsExactly(1L, 2L);
    }

    /**
     * 특정 time_detail에 필요한 인원보다 가능한 근무자가 적으면 탐색 없이 빈 결과를 반환한다.
     */
    @Test
    void generate_returnsEmptyWhenAnySlotHasTooFewAvailableWorkers() {
        WeekSchedule weekSchedule = createWeekSchedule(0, 10);
        Day day = createDay(weekSchedule, 1L);
        TimeDetail timeDetail = createTimeDetail(day, 1L, 1);

        ScheduleCandidateGenerationResult result = measure("impossible-slot", () ->
            generator.generate(new ScheduleCandidateGenerationCommand(
                weekSchedule,
                List.of(timeDetail),
                List.of(1L, 2L, 3L),
                Map.of(timeDetail.getId(), Set.of(1L, 2L))
            ))
        );

        assertThat(result.algorithmVersion()).isEqualTo(EXPECTED_ALGORITHM_VERSION);
        assertThat(result.candidates()).isEmpty();
    }

    /**
     * 생성된 후보는 품질 점수를 가지며 높은 점수 순서로 반환된다.
     */
    @Test
    void generate_scoresCandidatesAndReturnsHigherScoresFirst() {
        WeekSchedule weekSchedule = createWeekSchedule(0, 4);
        Day day = createDay(weekSchedule, 1L);

        List<TimeDetail> timeDetails = List.of(
            createTimeDetail(day, 1L, 1, 1),
            createTimeDetail(day, 2L, 2, 1),
            createTimeDetail(day, 3L, 3, 1),
            createTimeDetail(day, 4L, 4, 1)
        );

        ScheduleCandidateGenerationResult result = measure("score-sorted", () ->
            generator.generate(new ScheduleCandidateGenerationCommand(
                weekSchedule,
                timeDetails,
                List.of(1L, 2L, 3L, 4L),
                Map.of()
            ))
        );

        assertThat(result.algorithmVersion()).isEqualTo(EXPECTED_ALGORITHM_VERSION);
        assertThat(result.candidates()).isNotEmpty();

        assertThat(result.candidates())
            .allSatisfy(candidate -> assertThat(candidate.score()).isPositive());

        assertScoresAreSortedDescending(result);
    }

    /**
     * 큰 탐색 공간에서도 제한 시간 안에 최대 후보 수 이하의 결과를 반환한다.
     */
    @Test
    void generate_handlesLargeSearchSpaceWithinBoundedTime() {
        WeekSchedule weekSchedule = createWeekSchedule(0, 8);
        Day day = createDay(weekSchedule, 1L);

        List<TimeDetail> timeDetails = IntStream.rangeClosed(1, 8)
            .mapToObj(workPartNo -> createTimeDetail(day, (long) workPartNo, workPartNo, 2))
            .toList();

        ScheduleCandidateGenerationResult result = assertTimeoutPreemptively(Duration.ofSeconds(5), () ->
            measure("large-search-space", () ->
                generator.generate(new ScheduleCandidateGenerationCommand(
                    weekSchedule,
                    timeDetails,
                    List.of(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L),
                    Map.of()
                ))
            )
        );

        assertThat(result.algorithmVersion()).isEqualTo(EXPECTED_ALGORITHM_VERSION);
        assertThat(result.candidates()).hasSizeLessThanOrEqualTo(MAX_CANDIDATE_COUNT);
        assertCandidateNumbersAreSequential(result);
    }

    /**
     * 근무자 10명과 하루 5개 파트, 근무 불가 조건이 있는 입력도 제한 시간 안에 처리한다.
     */
    @Test
    void generate_handlesTenWorkersAndFiveDailyPartsWithUnavailableConditions() {
        WeekSchedule weekSchedule = createWeekSchedule(0, 5);
        Day day = createDay(weekSchedule, 1L);

        List<TimeDetail> timeDetails = IntStream.rangeClosed(1, 5)
            .mapToObj(workPartNo -> createTimeDetail(day, (long) workPartNo, workPartNo, 2))
            .toList();

        List<Long> workerMemberIds = List.of(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L);

        Map<Long, Set<Long>> unavailableWorkerIdsByTimeDetailId = Map.of(
            1L, Set.of(1L, 2L, 3L),
            2L, Set.of(3L, 4L, 5L),
            3L, Set.of(5L, 6L, 7L),
            4L, Set.of(7L, 8L, 9L),
            5L, Set.of(2L, 9L, 10L)
        );

        ScheduleCandidateGenerationResult result = assertTimeoutPreemptively(Duration.ofSeconds(5), () ->
            measure("10-workers-5-parts", () ->
                generator.generate(new ScheduleCandidateGenerationCommand(
                    weekSchedule,
                    timeDetails,
                    workerMemberIds,
                    unavailableWorkerIdsByTimeDetailId
                ))
            )
        );

        assertThat(result.algorithmVersion()).isEqualTo(EXPECTED_ALGORITHM_VERSION);
        assertThat(result.candidates()).isNotEmpty();
        assertThat(result.candidates()).hasSizeLessThanOrEqualTo(MAX_CANDIDATE_COUNT);

        assertCandidateNumbersAreSequential(result);
        assertScoresAreSortedDescending(result);

        result.candidates().forEach(candidate ->
            assertCandidateIsValid(
                candidate,
                timeDetails,
                workerMemberIds,
                unavailableWorkerIdsByTimeDetailId,
                weekSchedule.getMinPersonalWorkCount(),
                weekSchedule.getMaxPersonalWorkCount()
            )
        );
    }

    /**
     * 여러 요일과 파트로 구성된 주간 스케줄 후보를 올바르게 생성한다.
     */
    @Test
    @DisplayName("7명 근무자와 여러 time_detail을 가진 주간 스케줄 후보를 생성한다")
    void fullWeek_exhaustivePruning_processedCorrectly() {
        WeekSchedule weekSchedule = createWeekSchedule(3, 7);

        Day wednesday = createDay(
            weekSchedule,
            1L,
            ScheduleDayName.WEDNESDAY,
            LocalDate.of(2025, 1, 8),
            3,
            false,
            false
        );

        Day thursday = createDay(
            weekSchedule,
            2L,
            ScheduleDayName.THURSDAY,
            LocalDate.of(2025, 1, 9),
            4,
            false,
            false
        );

        Day friday = createDay(
            weekSchedule,
            3L,
            ScheduleDayName.FRIDAY,
            LocalDate.of(2025, 1, 10),
            5,
            false,
            false
        );

        Day saturday = createDay(
            weekSchedule,
            4L,
            ScheduleDayName.SATURDAY,
            LocalDate.of(2025, 1, 11),
            6,
            false,
            false
        );

        Day sunday = createDay(
            weekSchedule,
            5L,
            ScheduleDayName.SUNDAY,
            LocalDate.of(2025, 1, 12),
            7,
            false,
            false
        );

        List<TimeDetail> timeDetails = List.of(
            createTimeDetail(wednesday, 1L, 1, 1),
            createTimeDetail(wednesday, 2L, 2, 2),
            createTimeDetail(wednesday, 3L, 3, 1),

            createTimeDetail(thursday, 4L, 1, 1),
            createTimeDetail(thursday, 5L, 2, 2),
            createTimeDetail(thursday, 6L, 3, 1),

            createTimeDetail(friday, 7L, 1, 2),
            createTimeDetail(friday, 8L, 2, 3),
            createTimeDetail(friday, 9L, 3, 3),

            createTimeDetail(saturday, 10L, 1, 3),
            createTimeDetail(saturday, 11L, 2, 3),
            createTimeDetail(saturday, 12L, 3, 3),

            createTimeDetail(sunday, 13L, 1, 2),
            createTimeDetail(sunday, 14L, 2, 3),
            createTimeDetail(sunday, 15L, 3, 2)
        );

        List<Long> workerMemberIds = List.of(1L, 2L, 3L, 4L, 5L, 6L, 7L);

        ScheduleCandidateGenerationResult result = assertTimeoutPreemptively(Duration.ofSeconds(5), () ->
            measure("full-week", () ->
                generator.generate(new ScheduleCandidateGenerationCommand(
                    weekSchedule,
                    timeDetails,
                    workerMemberIds,
                    Map.of()
                ))
            )
        );

        assertThat(result.algorithmVersion()).isEqualTo(EXPECTED_ALGORITHM_VERSION);
        assertThat(result.candidates()).isNotEmpty();
        assertThat(result.candidates()).hasSizeLessThanOrEqualTo(MAX_CANDIDATE_COUNT);

        assertCandidateNumbersAreSequential(result);
        assertScoresAreSortedDescending(result);

        result.candidates().forEach(candidate ->
            assertCandidateIsValid(
                candidate,
                timeDetails,
                workerMemberIds,
                Map.of(),
                weekSchedule.getMinPersonalWorkCount(),
                weekSchedule.getMaxPersonalWorkCount()
            )
        );
    }

    /**
     * 7일 전체와 요일별 3개 파트, 근무자 10명, 순환 근무 불가 조건을 제한 시간 안에 처리한다.
     */
    @Test
    @DisplayName("7일 전체 스케줄과 근무 불가 조건을 가진 밀집 입력을 제한 시간 안에 처리한다")
    void generate_handlesDenseFullWeekWithUnavailableConditionsWithinBoundedTime() {
        WeekSchedule weekSchedule = createWeekSchedule(2, 5);

        List<Day> days = createFullWeekDays(weekSchedule);

        List<TimeDetail> timeDetails = createTimeDetailsForDays(
            days,
            List.of(2, 1, 2)
        );

        List<Long> workerMemberIds = LongStream.rangeClosed(1, 10)
            .boxed()
            .toList();

        Map<Long, Set<Long>> unavailableWorkerIdsByTimeDetailId =
            createCyclicUnavailableMap(timeDetails, workerMemberIds, 4);

        ScheduleCandidateGenerationResult result = assertTimeoutPreemptively(Duration.ofSeconds(2), () ->
            measure("dense-full-week", () ->
                generator.generate(new ScheduleCandidateGenerationCommand(
                    weekSchedule,
                    timeDetails,
                    workerMemberIds,
                    unavailableWorkerIdsByTimeDetailId
                ))
            )
        );

        assertThat(result.algorithmVersion()).isEqualTo(EXPECTED_ALGORITHM_VERSION);
        assertThat(result.candidates()).isNotEmpty();
        assertThat(result.candidates()).hasSizeLessThanOrEqualTo(MAX_CANDIDATE_COUNT);

        assertCandidateNumbersAreSequential(result);
        assertScoresAreSortedDescending(result);

        result.candidates().forEach(candidate ->
            assertCandidateIsValid(
                candidate,
                timeDetails,
                workerMemberIds,
                unavailableWorkerIdsByTimeDetailId,
                weekSchedule.getMinPersonalWorkCount(),
                weekSchedule.getMaxPersonalWorkCount()
            )
        );
    }

    /**
     * 테스트 케이스별 알고리즘 수행 시간을 출력한다.
     */
    private ScheduleCandidateGenerationResult measure(
        String testCaseName,
        Supplier<ScheduleCandidateGenerationResult> supplier
    ) {
        long startedAt = System.nanoTime();
        ScheduleCandidateGenerationResult result = supplier.get();
        long elapsedNanos = System.nanoTime() - startedAt;

        System.out.printf(
            "[ScheduleGeneratorPerf] %-20s | elapsedMs=%8.3f | candidates=%3d | algorithm=%s%n",
            testCaseName,
            elapsedNanos / 1_000_000.0,
            result.candidates().size(),
            result.algorithmVersion()
        );

        return result;
    }

    private void assertCandidateNumbersAreSequential(ScheduleCandidateGenerationResult result) {
        assertThat(result.candidates())
            .extracting(ScheduleCandidate::candidateNo)
            .containsExactlyElementsOf(
                IntStream.rangeClosed(1, result.candidates().size())
                    .boxed()
                    .toList()
            );
    }

    private void assertScoresAreSortedDescending(ScheduleCandidateGenerationResult result) {
        assertThat(result.candidates())
            .extracting(ScheduleCandidate::score)
            .isSortedAccordingTo(Comparator.reverseOrder());
    }

    private void assertCandidateIsValid(
        ScheduleCandidate candidate,
        List<TimeDetail> sourceTimeDetails,
        List<Long> workerMemberIds,
        Map<Long, Set<Long>> unavailableWorkerIdsByTimeDetailId,
        int minPersonalWorkCount,
        int maxPersonalWorkCount
    ) {
        Map<Long, TimeDetail> timeDetailById = sourceTimeDetails.stream()
            .collect(Collectors.toMap(TimeDetail::getId, timeDetail -> timeDetail));

        List<ScheduleCandidateTimeDetail> assignedTimeDetails = candidate.days().stream()
            .flatMap(day -> day.timeDetails().stream())
            .toList();

        assertThat(assignedTimeDetails)
            .extracting(ScheduleCandidateTimeDetail::timeDetailId)
            .containsExactlyElementsOf(
                sourceTimeDetails.stream()
                    .map(TimeDetail::getId)
                    .toList()
            );

        Map<Long, Integer> workCountByWorkerId = new HashMap<>();
        workerMemberIds.forEach(workerMemberId -> workCountByWorkerId.put(workerMemberId, 0));

        for (ScheduleCandidateTimeDetail assignedTimeDetail : assignedTimeDetails) {
            TimeDetail sourceTimeDetail = timeDetailById.get(assignedTimeDetail.timeDetailId());

            assertThat(sourceTimeDetail)
                .as("source time_detail must exist. timeDetailId=%s", assignedTimeDetail.timeDetailId())
                .isNotNull();

            assertThat(assignedTimeDetail.workerMemberIds())
                .as("assigned worker count must match required worker count. timeDetailId=%s",
                    assignedTimeDetail.timeDetailId())
                .hasSize(sourceTimeDetail.getWorkerCount());

            assertThat(assignedTimeDetail.workerMemberIds())
                .as("same worker must not be assigned twice in one time_detail. timeDetailId=%s",
                    assignedTimeDetail.timeDetailId())
                .doesNotHaveDuplicates();

            assertThat(assignedTimeDetail.workerMemberIds())
                .as("assigned worker must be one of command workerMemberIds. timeDetailId=%s",
                    assignedTimeDetail.timeDetailId())
                .allMatch(workerMemberIds::contains);

            assertThat(assignedTimeDetail.workerMemberIds())
                .as("unavailable worker must not be assigned. timeDetailId=%s",
                    assignedTimeDetail.timeDetailId())
                .noneMatch(workerMemberId ->
                    unavailableWorkerIdsByTimeDetailId
                        .getOrDefault(assignedTimeDetail.timeDetailId(), Set.of())
                        .contains(workerMemberId)
                );

            assignedTimeDetail.workerMemberIds()
                .forEach(workerMemberId -> workCountByWorkerId.merge(workerMemberId, 1, Integer::sum));
        }

        assertThat(workCountByWorkerId.values())
            .as("each worker's work count must satisfy min/max condition")
            .allSatisfy(workCount -> {
                assertThat(workCount).isGreaterThanOrEqualTo(minPersonalWorkCount);
                assertThat(workCount).isLessThanOrEqualTo(maxPersonalWorkCount);
            });
    }

    private WeekSchedule createWeekSchedule(int minPersonalWorkCount, int maxPersonalWorkCount) {
        return WeekSchedule.create(
            null,
            "테스트 주차",
            LocalDate.of(2026, 7, 5),
            LocalTime.of(9, 0),
            LocalTime.of(22, 0),
            minPersonalWorkCount,
            maxPersonalWorkCount
        );
    }

    private Day createDay(WeekSchedule weekSchedule, Long dayId) {
        return createDay(
            weekSchedule,
            dayId,
            ScheduleDayName.MONDAY,
            LocalDate.of(2026, 7, 6),
            1,
            false,
            false
        );
    }

    private Day createDay(
        WeekSchedule weekSchedule,
        Long dayId,
        ScheduleDayName dayName,
        LocalDate date,
        int groupingId,
        boolean holidayStatus,
        boolean selectLimitStatus
    ) {
        Day day = Day.create(
            weekSchedule,
            dayName,
            date,
            groupingId,
            1,
            holidayStatus,
            selectLimitStatus
        );

        ReflectionTestUtils.setField(day, "id", dayId);
        return day;
    }

    private TimeDetail createTimeDetail(Day day, Long timeDetailId, int workPartNo) {
        return createTimeDetail(day, timeDetailId, workPartNo, 2);
    }

    private TimeDetail createTimeDetail(
        Day day,
        Long timeDetailId,
        int workPartNo,
        int workerCount
    ) {
        TimeDetail timeDetail = TimeDetail.create(
            day,
            workPartNo,
            "파트" + workPartNo,
            workerCount,
            LocalTime.of(9 + workPartNo, 0),
            LocalTime.of(10 + workPartNo, 0),
            0
        );

        ReflectionTestUtils.setField(timeDetail, "id", timeDetailId);
        return timeDetail;
    }

    private List<Day> createFullWeekDays(WeekSchedule weekSchedule) {
        return List.of(
            createDay(
                weekSchedule,
                1L,
                ScheduleDayName.MONDAY,
                LocalDate.of(2026, 7, 6),
                1,
                false,
                false
            ),
            createDay(
                weekSchedule,
                2L,
                ScheduleDayName.TUESDAY,
                LocalDate.of(2026, 7, 7),
                2,
                false,
                false
            ),
            createDay(
                weekSchedule,
                3L,
                ScheduleDayName.WEDNESDAY,
                LocalDate.of(2026, 7, 8),
                3,
                false,
                false
            ),
            createDay(
                weekSchedule,
                4L,
                ScheduleDayName.THURSDAY,
                LocalDate.of(2026, 7, 9),
                4,
                false,
                false
            ),
            createDay(
                weekSchedule,
                5L,
                ScheduleDayName.FRIDAY,
                LocalDate.of(2026, 7, 10),
                5,
                false,
                false
            ),
            createDay(
                weekSchedule,
                6L,
                ScheduleDayName.SATURDAY,
                LocalDate.of(2026, 7, 11),
                6,
                false,
                false
            ),
            createDay(
                weekSchedule,
                7L,
                ScheduleDayName.SUNDAY,
                LocalDate.of(2026, 7, 12),
                7,
                false,
                false
            )
        );
    }

    private List<TimeDetail> createTimeDetailsForDays(
        List<Day> days,
        List<Integer> workerCountsByPart
    ) {
        AtomicLong timeDetailId = new AtomicLong(1L);

        return days.stream()
            .flatMap(day ->
                IntStream.range(0, workerCountsByPart.size())
                    .mapToObj(index ->
                        createTimeDetail(
                            day,
                            timeDetailId.getAndIncrement(),
                            index + 1,
                            workerCountsByPart.get(index)
                        )
                    )
            )
            .toList();
    }

    private Map<Long, Set<Long>> createCyclicUnavailableMap(
        List<TimeDetail> timeDetails,
        List<Long> workerMemberIds,
        int unavailableCount
    ) {
        Map<Long, Set<Long>> unavailableWorkerIdsByTimeDetailId = new HashMap<>();

        for (int i = 0; i < timeDetails.size(); i++) {
            int startIndex = i;

            Set<Long> unavailableWorkerIds = IntStream.range(0, unavailableCount)
                .mapToObj(offset -> workerMemberIds.get((startIndex + offset) % workerMemberIds.size()))
                .collect(Collectors.toSet());

            unavailableWorkerIdsByTimeDetailId.put(timeDetails.get(i).getId(), unavailableWorkerIds);
        }

        return unavailableWorkerIdsByTimeDetailId;
    }

    private void assertEveryWorkerAssignedExactly(
        ScheduleCandidate candidate,
        List<Long> workerMemberIds,
        int expectedWorkCount
    ) {
        Map<Long, Long> workCountByWorkerId = candidate.days().stream()
            .flatMap(day -> day.timeDetails().stream())
            .flatMap(timeDetail -> timeDetail.workerMemberIds().stream())
            .collect(Collectors.groupingBy(workerMemberId -> workerMemberId, Collectors.counting()));

        assertThat(workerMemberIds)
            .allSatisfy(workerMemberId ->
                assertThat(workCountByWorkerId.getOrDefault(workerMemberId, 0L))
                    .as("workerId=%s must be assigned exactly %s times",
                        workerMemberId,
                        expectedWorkCount)
                    .isEqualTo(expectedWorkCount)
            );
    }
}
