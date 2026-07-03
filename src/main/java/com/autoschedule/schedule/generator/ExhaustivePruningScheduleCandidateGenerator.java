package com.autoschedule.schedule.generator;

import com.autoschedule.schedulecondition.domain.TimeDetail;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 완전탐색과 최대 근무 횟수 가지치기를 사용하는 MVP 자동 스케줄 후보 생성 구현체이다.
 */
public class ExhaustivePruningScheduleCandidateGenerator implements ScheduleCandidateGenerator {

    private static final String ALGORITHM_VERSION = "MVP_EXHAUSTIVE_V1";

    /**
     * 가능한 근무자 조합을 탐색하고 주간 최소/최대 근무 횟수 조건을 만족하는 후보를 반환한다.
     */
    @Override
    public ScheduleCandidateGenerationResult generate(ScheduleCandidateGenerationCommand command) {
        List<ScheduleCandidate> candidates = new ArrayList<>();
        searchCandidates(
                0,
                command.timeDetails(),
                command.workerMemberIds().stream().sorted().toList(),
                command.unavailableWorkerIdsByTimeDetailId(),
                command.weekSchedule().getMinPersonalWorkCount(),
                command.weekSchedule().getMaxPersonalWorkCount(),
                new LinkedHashMap<>(),
                new HashMap<>(),
                candidates
        );

        for (int index = 0; index < candidates.size(); index++) {
            candidates.set(index, candidates.get(index).withCandidateNo(index + 1));
        }
        return new ScheduleCandidateGenerationResult(ALGORITHM_VERSION, candidates);
    }

    /**
     * DFS로 각 time_detail에 들어갈 근무자 조합을 선택한다.
     */
    private void searchCandidates(
            int index,
            List<TimeDetail> timeDetails,
            List<Long> workerMemberIds,
            Map<Long, Set<Long>> unavailableWorkerIdsByTimeDetailId,
            int minPersonalWorkCount,
            int maxPersonalWorkCount,
            Map<Long, List<Long>> selectedWorkerIdsByTimeDetailId,
            Map<Long, Integer> workCountByMemberId,
            List<ScheduleCandidate> candidates
    ) {
        if (index == timeDetails.size()) {
            boolean satisfiesMinimum = workerMemberIds.stream()
                    .allMatch(memberId -> workCountByMemberId.getOrDefault(memberId, 0) >= minPersonalWorkCount);
            if (satisfiesMinimum) {
                candidates.add(createCandidate(selectedWorkerIdsByTimeDetailId, timeDetails));
            }
            return;
        }

        TimeDetail timeDetail = timeDetails.get(index);
        Set<Long> unavailableMemberIds = unavailableWorkerIdsByTimeDetailId.getOrDefault(timeDetail.getId(), Set.of());
        List<Long> availableMemberIds = workerMemberIds.stream()
                .filter(memberId -> !unavailableMemberIds.contains(memberId))
                .toList();

        for (List<Long> combination : combinations(availableMemberIds, timeDetail.getWorkerCount())) {
            if (exceedsMaxWorkCount(combination, workCountByMemberId, maxPersonalWorkCount)) {
                continue;
            }

            selectedWorkerIdsByTimeDetailId.put(timeDetail.getId(), combination);
            combination.forEach(memberId -> workCountByMemberId.merge(memberId, 1, Integer::sum));

            searchCandidates(
                    index + 1,
                    timeDetails,
                    workerMemberIds,
                    unavailableWorkerIdsByTimeDetailId,
                    minPersonalWorkCount,
                    maxPersonalWorkCount,
                    selectedWorkerIdsByTimeDetailId,
                    workCountByMemberId,
                    candidates
            );

            combination.forEach(memberId ->
                    workCountByMemberId.computeIfPresent(memberId, (key, count) -> count == 1 ? null : count - 1)
            );
            selectedWorkerIdsByTimeDetailId.remove(timeDetail.getId());
        }
    }

    /**
     * 선택된 time_detail별 근무자 목록을 후보 JSON 모델로 변환한다.
     */
    private ScheduleCandidate createCandidate(
            Map<Long, List<Long>> selectedWorkerIdsByTimeDetailId,
            List<TimeDetail> timeDetails
    ) {
        Map<Long, TimeDetail> timeDetailById = timeDetails.stream()
                .collect(Collectors.toMap(TimeDetail::getId, timeDetail -> timeDetail));
        Map<Long, List<ScheduleCandidateTimeDetail>> timeDetailsByDayId = new LinkedHashMap<>();

        selectedWorkerIdsByTimeDetailId.forEach((timeDetailId, workerMemberIds) -> {
            TimeDetail timeDetail = timeDetailById.get(timeDetailId);
            timeDetailsByDayId
                    .computeIfAbsent(timeDetail.getDay().getId(), ignored -> new ArrayList<>())
                    .add(new ScheduleCandidateTimeDetail(timeDetailId, workerMemberIds));
        });

        List<ScheduleCandidateDay> days = timeDetailsByDayId.entrySet().stream()
                .map(entry -> new ScheduleCandidateDay(entry.getKey(), entry.getValue()))
                .toList();
        return new ScheduleCandidate(0, 0, days);
    }

    /**
     * 특정 조합이 주간 최대 근무 횟수를 넘는지 확인한다.
     */
    private boolean exceedsMaxWorkCount(
            List<Long> combination,
            Map<Long, Integer> workCountByMemberId,
            int maxPersonalWorkCount
    ) {
        return combination.stream()
                .anyMatch(memberId -> workCountByMemberId.getOrDefault(memberId, 0) + 1 > maxPersonalWorkCount);
    }

    /**
     * 주어진 근무자 목록에서 필요한 인원 수만큼의 조합을 만든다.
     */
    private List<List<Long>> combinations(List<Long> source, int size) {
        if (source.size() < size) {
            return List.of();
        }
        List<List<Long>> result = new ArrayList<>();
        collectCombinations(source, size, 0, new ArrayList<>(), result);
        return result;
    }

    /**
     * 조합 생성을 위한 재귀 함수를 수행한다.
     */
    private void collectCombinations(
            List<Long> source,
            int size,
            int start,
            List<Long> current,
            List<List<Long>> result
    ) {
        if (current.size() == size) {
            result.add(List.copyOf(current));
            return;
        }

        for (int index = start; index < source.size(); index++) {
            current.add(source.get(index));
            collectCombinations(source, size, index + 1, current, result);
            current.remove(current.size() - 1);
        }
    }
}
