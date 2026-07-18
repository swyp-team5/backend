package com.autoschedule.schedule.generator;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ScheduleCandidateSelectorTest {

    private final ScheduleCandidateSelector selector = new ScheduleCandidateSelector();

    /**
     * 후보 번호와 점수가 달라도 실제 슬롯 배정이 같으면 한 건만 유지한다.
     */
    @Test
    void select_removesCandidatesWithSameAssignments() {
        ScheduleCandidate original = candidate(1, 100, List.of(1L, 2L, 1L, 2L));
        ScheduleCandidate duplicate = candidate(2, 90, List.of(1L, 2L, 1L, 2L));

        List<ScheduleCandidate> selected = selector.select(
            List.of(original, duplicate),
            List.of(1L, 2L),
            50
        );

        assertThat(selected).hasSize(1);
        assertThat(selected.get(0).candidateNo()).isEqualTo(1);
    }

    /**
     * 기존 점수보다 근무자별 파트 배정 횟수의 균형을 우선한다.
     */
    @Test
    void select_prioritizesBalancedPartAssignmentCounts() {
        ScheduleCandidate biased = candidate(1, 1_000_000, List.of(1L, 1L, 1L, 1L, 1L, 1L));
        ScheduleCandidate balanced = candidate(2, 1, List.of(1L, 2L, 1L, 2L, 1L, 2L));

        List<ScheduleCandidate> selected = selector.select(
            List.of(biased, balanced),
            List.of(1L, 2L),
            2
        );

        assertThat(selected)
            .extracting(ScheduleCandidate::candidateNo)
            .containsExactly(2, 1);
    }

    /**
     * 배정 횟수 분포가 같으면 기존 선택 후보와 더 다른 후보를 우선한다.
     */
    @Test
    void select_choosesDistantSchedulesWhenFairnessIsEqual() {
        ScheduleCandidate first = candidate(1, 100, List.of(1L, 1L, 1L, 2L, 2L, 2L));
        ScheduleCandidate nearFirst = candidate(2, 99, List.of(2L, 1L, 1L, 1L, 2L, 2L));
        ScheduleCandidate farFromFirst = candidate(3, 98, List.of(2L, 2L, 2L, 1L, 1L, 1L));

        List<ScheduleCandidate> selected = selector.select(
            List.of(first, nearFirst, farFromFirst),
            List.of(1L, 2L),
            2
        );

        assertThat(selected)
            .extracting(ScheduleCandidate::candidateNo)
            .containsExactly(1, 3);
    }

    /**
     * 단일 근무자가 배정된 테스트 후보를 생성한다.
     */
    private ScheduleCandidate candidate(
        int candidateNo,
        int score,
        List<Long> workerIdsByTimeDetail
    ) {
        List<ScheduleCandidateTimeDetail> timeDetails = new ArrayList<>(workerIdsByTimeDetail.size());
        for (int index = 0; index < workerIdsByTimeDetail.size(); index++) {
            timeDetails.add(new ScheduleCandidateTimeDetail(
                (long) index + 1,
                List.of(workerIdsByTimeDetail.get(index))
            ));
        }

        return new ScheduleCandidate(
            candidateNo,
            score,
            List.of(new ScheduleCandidateDay(1L, timeDetails))
        );
    }
}
