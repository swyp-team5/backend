package com.autoschedule.schedule.generator;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 근무자별 time_detail 배정 개수만으로 후보의 공정성을 비교한다.
 */
record ScheduleCandidateQuality(int assignmentRange, long squaredAssignmentSum)
    implements Comparable<ScheduleCandidateQuality> {

    /**
     * 배정되지 않은 근무자도 0회로 포함해 공정성 지표를 계산한다.
     */
    static ScheduleCandidateQuality from(
        ScheduleCandidate candidate,
        List<Long> workerMemberIds
    ) {
        Map<Long, Integer> assignmentCountByWorker = new HashMap<>();
        workerMemberIds.forEach(workerMemberId -> assignmentCountByWorker.put(workerMemberId, 0));

        candidate.days().stream()
            .flatMap(day -> day.timeDetails().stream())
            .flatMap(timeDetail -> timeDetail.workerMemberIds().stream())
            .forEach(workerMemberId -> assignmentCountByWorker.merge(workerMemberId, 1, Integer::sum));

        int minimum = Integer.MAX_VALUE;
        int maximum = Integer.MIN_VALUE;
        long squaredSum = 0L;

        for (int assignmentCount : assignmentCountByWorker.values()) {
            minimum = Math.min(minimum, assignmentCount);
            maximum = Math.max(maximum, assignmentCount);
            squaredSum += (long) assignmentCount * assignmentCount;
        }

        if (assignmentCountByWorker.isEmpty()) {
            return new ScheduleCandidateQuality(0, 0L);
        }

        return new ScheduleCandidateQuality(maximum - minimum, squaredSum);
    }

    /**
     * 배정 범위가 작고 제곱합이 작은 후보를 더 공정한 후보로 정렬한다.
     */
    @Override
    public int compareTo(ScheduleCandidateQuality other) {
        int rangeComparison = Integer.compare(assignmentRange, other.assignmentRange);
        if (rangeComparison != 0) {
            return rangeComparison;
        }
        return Long.compare(squaredAssignmentSum, other.squaredAssignmentSum);
    }
}
