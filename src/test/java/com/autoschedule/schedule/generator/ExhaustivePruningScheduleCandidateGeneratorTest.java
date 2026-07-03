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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class ExhaustivePruningScheduleCandidateGeneratorTest {

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

        assertThat(result.candidates()).hasSize(50);
        assertThat(result.candidates())
                .extracting(ScheduleCandidate::candidateNo)
                .containsExactlyElementsOf(IntStream.rangeClosed(1, 50).boxed().toList());
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

        assertThat(result.candidates()).isNotEmpty();
        assertThat(result.candidates())
                .allSatisfy(candidate -> assertThat(candidate.score()).isPositive());
        assertThat(result.candidates())
                .extracting(ScheduleCandidate::score)
                .isSortedAccordingTo(Comparator.reverseOrder());
    }

    /**
     * 큰 탐색 공간에서도 후보 수 제한과 가지치기로 제한 시간 안에 결과를 반환한다.
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

        assertThat(result.candidates()).hasSizeLessThanOrEqualTo(50);
    }

    /**
     * 근무자 10명, 하루 5개 파트, 파트별 근무 불가 조건이 있는 현실형 입력을 제한 시간 안에 처리한다.
     */
    @Test
    void generate_handlesTenWorkersAndFiveDailyPartsWithUnavailableConditions() {
        WeekSchedule weekSchedule = createWeekSchedule(0, 5);
        Day day = createDay(weekSchedule, 1L);
        List<TimeDetail> timeDetails = IntStream.rangeClosed(1, 5)
                .mapToObj(workPartNo -> createTimeDetail(day, (long) workPartNo, workPartNo, 2))
                .toList();

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
                                List.of(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L),
                                unavailableWorkerIdsByTimeDetailId
                        ))
                )
        );

        assertThat(result.candidates()).isNotEmpty();
        assertThat(result.candidates()).hasSizeLessThanOrEqualTo(50);
        assertThat(result.candidates())
                .extracting(ScheduleCandidate::score)
                .isSortedAccordingTo(Comparator.reverseOrder());
    }

    /**
     * 생성기 계산 시간을 보기 좋은 형식으로 출력한다.
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

    private WeekSchedule createWeekSchedule(int minPersonalWorkCount, int maxPersonalWorkCount) {
        return WeekSchedule.create(
                null,
                "테스트 주간",
                LocalDate.of(2026, 7, 5),
                LocalTime.of(9, 0),
                LocalTime.of(22, 0),
                minPersonalWorkCount,
                maxPersonalWorkCount
        );
    }

    private Day createDay(WeekSchedule weekSchedule, Long dayId) {
        Day day = Day.create(
                weekSchedule,
                ScheduleDayName.MONDAY,
                LocalDate.of(2026, 7, 6),
                1,
                1,
                false,
                false
        );
        ReflectionTestUtils.setField(day, "id", dayId);
        return day;
    }

    private TimeDetail createTimeDetail(Day day, Long timeDetailId, int workPartNo) {
        return createTimeDetail(day, timeDetailId, workPartNo, 2);
    }

    private TimeDetail createTimeDetail(Day day, Long timeDetailId, int workPartNo, int workerCount) {
        TimeDetail timeDetail = TimeDetail.create(
                day,
                workPartNo,
                "타임" + workPartNo,
                workerCount,
                LocalTime.of(9 + workPartNo, 0),
                LocalTime.of(10 + workPartNo, 0),
                0
        );
        ReflectionTestUtils.setField(timeDetail, "id", timeDetailId);
        return timeDetail;
    }
}
