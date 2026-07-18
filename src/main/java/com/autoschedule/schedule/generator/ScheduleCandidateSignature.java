package com.autoschedule.schedule.generator;

import java.util.Comparator;
import java.util.List;

/**
 * 후보 번호나 점수를 제외하고 실제 슬롯별 근무자 배정만으로 후보를 식별한다.
 */
record ScheduleCandidateSignature(List<SlotAssignment> assignments) {

    /**
     * 응답 순서와 무관하게 비교할 수 있도록 timeDetailId와 memberId를 정렬한다.
     */
    static ScheduleCandidateSignature from(ScheduleCandidate candidate) {
        List<SlotAssignment> assignments = candidate.days().stream()
            .flatMap(day -> day.timeDetails().stream())
            .map(timeDetail -> new SlotAssignment(
                timeDetail.timeDetailId(),
                timeDetail.workerMemberIds().stream().sorted().toList()
            ))
            .sorted(Comparator.comparingLong(SlotAssignment::timeDetailId))
            .toList();

        return new ScheduleCandidateSignature(assignments);
    }

    /**
     * 두 후보에서 근무자 구성이 다른 time_detail 수를 계산한다.
     */
    int distanceTo(ScheduleCandidateSignature other) {
        int leftIndex = 0;
        int rightIndex = 0;
        int distance = 0;

        while (leftIndex < assignments.size() && rightIndex < other.assignments.size()) {
            SlotAssignment left = assignments.get(leftIndex);
            SlotAssignment right = other.assignments.get(rightIndex);

            int idComparison = Long.compare(left.timeDetailId(), right.timeDetailId());
            if (idComparison == 0) {
                if (!left.workerMemberIds().equals(right.workerMemberIds())) {
                    distance++;
                }
                leftIndex++;
                rightIndex++;
            } else if (idComparison < 0) {
                distance++;
                leftIndex++;
            } else {
                distance++;
                rightIndex++;
            }
        }

        return distance + assignments.size() - leftIndex + other.assignments.size() - rightIndex;
    }

    /**
     * 단일 time_detail의 정규화된 근무자 배정을 표현한다.
     */
    record SlotAssignment(long timeDetailId, List<Long> workerMemberIds) {
    }
}
