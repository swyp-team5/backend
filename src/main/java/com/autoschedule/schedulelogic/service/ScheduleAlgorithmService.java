package com.autoschedule.schedulelogic.service;

import com.autoschedule.schedulecondition.domain.TimeDetail;
import com.autoschedule.schedulelogic.dto.AssignedWorkerDto;
import com.autoschedule.schedulelogic.dto.ScheduleAssignmentDto;
import com.autoschedule.schedulelogic.dto.ScheduleResultDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ScheduleAlgorithmService {

    private static final int MAX_RESULT_COUNT = 100;

    /**
     * 백트래킹 알고리즘을 실행하여 가능한 스케줄 경우의 수를 반환한다.
     * 결과는 점수 내림차순, 동일 점수 내에서는 배정 분산 오름차순으로 정렬된다.
     *
     * @param timeDetails    스케줄링 대상 time_detail 목록
     * @param unavailableMap 근무자별 불가 time_detail_id Set (key: memberId)
     * @param memberNameMap  근무자 ID → 이름 매핑
     * @param minWorkCount   근무자 1인당 최소 배정 횟수
     * @param maxWorkCount   근무자 1인당 최대 배정 횟수
     */
    public List<ScheduleResultDto> execute(
            List<TimeDetail> timeDetails,
            Map<Long, Set<Long>> unavailableMap,
            Map<Long, String> memberNameMap,
            int minWorkCount,
            int maxWorkCount
    ) {
        List<Long> memberIds = new ArrayList<>(memberNameMap.keySet());

        // 근무자별 현재 배정 횟수 추적 (초기값 0)
        Map<Long, Integer> workCountMap = new HashMap<>();
        memberIds.forEach(id -> workCountMap.put(id, 0));

        // time_detail별 배정된 근무자 ID 목록 추적 (삽입 순서 유지)
        Map<Long, List<Long>> assignmentMap = new LinkedHashMap<>();
        timeDetails.forEach(td -> assignmentMap.put(td.getId(), new ArrayList<>()));

        List<ScheduleResultDto> results = new ArrayList<>();

        backtrack(
                timeDetails, 0,
                unavailableMap, memberNameMap, memberIds,
                workCountMap, assignmentMap,
                minWorkCount, maxWorkCount,
                results
        );

        // 점수 내림차순 → 동일 점수 내 배정 분산 오름차순 정렬
        results.sort(
                Comparator.comparingInt(ScheduleResultDto::score).reversed()
                        .thenComparingDouble(ScheduleResultDto::assignmentVariance)
        );

        return results;
    }

    /**
     * DFS 기반 백트래킹으로 time_detail 목록을 순서대로 탐색하며 배정 조합을 구성한다.
     * 각 time_detail마다 배정 가능한 근무자 조합을 시도하고,
     * 조건을 만족하지 못하는 경우 해당 가지를 포기(가지치기)한다.
     *
     * @param index 현재 탐색 중인 time_detail 인덱스
     */
    private void backtrack(
            List<TimeDetail> timeDetails,
            int index,
            Map<Long, Set<Long>> unavailableMap,
            Map<Long, String> memberNameMap,
            List<Long> memberIds,
            Map<Long, Integer> workCountMap,
            Map<Long, List<Long>> assignmentMap,
            int minWorkCount,
            int maxWorkCount,
            List<ScheduleResultDto> results
    ) {
        // 결과가 최대 개수에 도달하면 더 이상 탐색하지 않음
        if (results.size() >= MAX_RESULT_COUNT) return;

        // 모든 time_detail 탐색 완료 → 필수 조건 검증 후 결과 저장
        if (index == timeDetails.size()) {

            // 필수 조건 1: 모든 근무자가 1회 이상 배정되어야 함
            boolean hasUnassignedWorker = memberIds.stream()
                    .anyMatch(id -> workCountMap.get(id) == 0);
            if (hasUnassignedWorker) return;

            // 필수 조건 2: 모든 근무자의 배정 횟수가 min/max 범위 내에 있어야 함
            boolean outOfRange = memberIds.stream()
                    .anyMatch(id -> workCountMap.get(id) < minWorkCount
                            || workCountMap.get(id) > maxWorkCount);
            if (outOfRange) return;

            results.add(buildResult(timeDetails, assignmentMap, memberNameMap, workCountMap));
            return;
        }

        TimeDetail current = timeDetails.get(index);

        // 휴일: 배정 없이 다음 time_detail로 넘어감
        if (current.getDay().isHolidayStatus()) {
            backtrack(timeDetails, index + 1, unavailableMap, memberNameMap, memberIds,
                    workCountMap, assignmentMap, minWorkCount, maxWorkCount, results);
            return;
        }

        // 선택제한: max 제약만 적용한 랜덤 조합으로 배정, 백트래킹 후 원복
        // 100가지 결과마다 다른 조합이 배정되도록 shuffle 적용
        if (current.getDay().isSelectLimitStatus()) {
            List<Long> available = memberIds.stream()
                    .filter(id -> !unavailableMap.getOrDefault(id, Set.of()).contains(current.getId()))
                    .filter(id -> workCountMap.get(id) < maxWorkCount)
                    .collect(Collectors.toCollection(ArrayList::new));

            int needed = current.getWorkerCount();
            List<List<Long>> combinations = getCombinations(available, needed);
            Collections.shuffle(combinations);

            for (List<Long> combination : combinations) {
                combination.forEach(id -> {
                    assignmentMap.get(current.getId()).add(id);
                    workCountMap.merge(id, 1, Integer::sum);
                });
                backtrack(timeDetails, index + 1, unavailableMap, memberNameMap, memberIds,
                        workCountMap, assignmentMap, minWorkCount, maxWorkCount, results);
                combination.forEach(id -> {
                    assignmentMap.get(current.getId()).remove(id);
                    workCountMap.merge(id, -1, Integer::sum);
                });
            }
            return;
        }

        // 일반 배정: 불가 time_detail 및 maxWorkCount 초과 근무자 제외 후 조합 탐색
        List<Long> available = memberIds.stream()
                .filter(id -> !unavailableMap.getOrDefault(id, Set.of()).contains(current.getId()))
                .filter(id -> workCountMap.get(id) < maxWorkCount)
                .toList();

        int needed = current.getWorkerCount();
        List<List<Long>> combinations = getCombinations(available, needed);

        // 결과 다양성 확보: 조합 순서를 랜덤하게 섞어 특정 근무자 편중 방지
        Collections.shuffle(combinations);

        // 배정 가능한 조합이 없는 경우: 미충족(isFulfilled=false)으로 두고 다음으로 진행
        if (combinations.isEmpty()) {
            backtrack(timeDetails, index + 1, unavailableMap, memberNameMap, memberIds,
                    workCountMap, assignmentMap, minWorkCount, maxWorkCount, results);
            return;
        }

        // 현재 time_detail 이후 남은 슬롯 수 (가지치기 판단에 사용)
        int remaining = timeDetails.size() - index - 1;

        for (List<Long> combination : combinations) {
            // 가지치기: 현재 combination 배정 후에도 남은 슬롯으로 모든 근무자가
            // minWorkCount를 채울 수 없으면 이 가지를 포기
            // combination에 포함된 근무자는 이번 배정으로 +1을 반영하여 판단
            Set<Long> combinationSet = new HashSet<>(combination);
            boolean canFulfillMin = memberIds.stream().allMatch(id -> {
                int countAfterThis = workCountMap.get(id) + (combinationSet.contains(id) ? 1 : 0);
                int stillNeeded = minWorkCount - countAfterThis;
                return stillNeeded <= 0 || remaining >= stillNeeded;
            });
            if (!canFulfillMin) continue;

            // 배정 적용
            combination.forEach(id -> {
                assignmentMap.get(current.getId()).add(id);
                workCountMap.merge(id, 1, Integer::sum);
            });

            backtrack(timeDetails, index + 1, unavailableMap, memberNameMap, memberIds,
                    workCountMap, assignmentMap, minWorkCount, maxWorkCount, results);

            // 백트래킹: 다음 조합 시도를 위해 배정 원복
            combination.forEach(id -> {
                assignmentMap.get(current.getId()).remove(id);
                workCountMap.merge(id, -1, Integer::sum);
            });
        }
    }

    /**
     * 백트래킹 탐색이 완료된 배정 상태로 ScheduleResultDto를 생성한다.
     * 점수는 workerCount를 충족한 time_detail 수 / 전체 time_detail 수 * 100으로 계산한다.
     * 분산은 근무자별 배정 횟수의 편차를 나타내며, 값이 작을수록 배정이 고르다는 의미다.
     */
    private ScheduleResultDto buildResult(
            List<TimeDetail> timeDetails,
            Map<Long, List<Long>> assignmentMap,
            Map<Long, String> memberNameMap,
            Map<Long, Integer> workCountMap
    ) {
        int fulfilledCount = 0;
        List<ScheduleAssignmentDto> assignments = new ArrayList<>();

        for (TimeDetail td : timeDetails) {
            // 휴일은 배정 대상이 아니므로 결과에서 제외
            if (td.getDay().isHolidayStatus()) continue;

            List<Long> assignedIds = assignmentMap.getOrDefault(td.getId(), List.of());

            // workerCount 이상 배정된 경우 충족으로 판단
            boolean isFulfilled = assignedIds.size() >= td.getWorkerCount();
            if (isFulfilled) fulfilledCount++;

            List<AssignedWorkerDto> assignedWorkers = assignedIds.stream()
                    .map(id -> new AssignedWorkerDto(id, memberNameMap.get(id)))
                    .toList();

            assignments.add(new ScheduleAssignmentDto(
                    td.getId(),
                    td.getDay().getDayName().name(),
                    td.getDay().getDate(),
                    td.getTimeName(),
                    td.getWorkerCount(),
                    assignedIds.size(),
                    isFulfilled,
                    assignedWorkers
            ));
        }

        // 점수: 휴일 제외 전체 time_detail 중 workerCount를 충족한 비율 * 100
        int totalCount = (int) timeDetails.stream()
                .filter(td -> !td.getDay().isHolidayStatus())
                .count();
        int score = totalCount == 0 ? 0 : (int) ((double) fulfilledCount / totalCount * 100);

        // 분산: 근무자별 배정 횟수의 평균 대비 편차 제곱의 평균
        double variance = 0.0;
        if (!workCountMap.isEmpty()) {
            double mean = workCountMap.values().stream()
                    .mapToInt(Integer::intValue)
                    .average()
                    .orElse(0.0);
            variance = workCountMap.values().stream()
                    .mapToDouble(count -> Math.pow(count - mean, 2))
                    .average()
                    .orElse(0.0);
        }

        return new ScheduleResultDto(score, variance, assignments);
    }

    /**
     * 리스트에서 size 크기의 모든 조합을 반환한다.
     * size가 0이면 빈 조합 1개를 반환하고, size가 리스트 크기를 초과하면 빈 리스트를 반환한다.
     */
    private List<List<Long>> getCombinations(List<Long> list, int size) {
        List<List<Long>> result = new ArrayList<>();

        if (size <= 0) {
            result.add(List.of());
            return result;
        }

        if (size > list.size()) return result;

        combinationHelper(list, size, 0, new ArrayList<>(), result);
        return result;
    }

    /**
     * 재귀적으로 조합을 구성하는 헬퍼 메서드.
     * start 인덱스부터 탐색하여 중복 없이 size 크기의 조합을 생성한다.
     */
    private void combinationHelper(
            List<Long> list, int size, int start,
            List<Long> current, List<List<Long>> result
    ) {
        if (current.size() == size) {
            result.add(new ArrayList<>(current));
            return;
        }

        for (int i = start; i < list.size(); i++) {
            current.add(list.get(i));
            combinationHelper(list, size, i + 1, current, result);
            current.remove(current.size() - 1);
        }
    }
}