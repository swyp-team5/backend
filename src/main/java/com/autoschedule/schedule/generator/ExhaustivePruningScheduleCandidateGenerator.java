package com.autoschedule.schedule.generator;

import com.autoschedule.schedulecondition.domain.TimeDetail;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * Bounded DFS + Dynamic MRV + Forced Worker + Score 기반 Top-N 자동 스케줄 후보 생성기.
 *
 * 성능 최적화 포인트:
 * 1. DFS 내부에서 Map<Long, Integer> 조회 제거
 * 2. 근무자 집합을 long bit mask로 표현
 * 3. 매 단계마다 Dynamic MRV로 가장 어려운 슬롯 선택
 * 4. 최소 근무 횟수 달성을 위해 반드시 들어가야 하는 forced worker 강제 포함
 * 5. 가능한 모든 후보를 만들지 않고 score 기반 Top-N만 유지
 */
public class ExhaustivePruningScheduleCandidateGenerator implements ScheduleCandidateGenerator {

    private static final String ALGORITHM_VERSION = "BIT_DFS_MRV_TOPN_V1";

    private static final int MAX_CANDIDATE_COUNT = 50;

    /**
     * 요청 1건이 서버 CPU를 과도하게 점유하지 않도록 제한한다.
     * 운영에서는 application.yml 설정값으로 분리하는 것을 추천한다.
     */
    private static final long MAX_SEARCH_NODE_COUNT = 2_000_000L;

    private static final int MAX_WORKER_COUNT_FOR_BIT_MASK = Long.SIZE;

    private static final long COMBINATION_COUNT_CAP = 1_000_000_000L;

    @Override
    public ScheduleCandidateGenerationResult generate(ScheduleCandidateGenerationCommand command) {
        List<Long> sortedWorkerMemberIds = command.workerMemberIds().stream()
            .sorted()
            .toList();

        if (sortedWorkerMemberIds.size() > MAX_WORKER_COUNT_FOR_BIT_MASK) {
            throw new IllegalArgumentException("Bit mask schedule generator supports up to 64 workers.");
        }

        List<TimeDetail> originalTimeDetails = command.timeDetails();

        int workerCount = sortedWorkerMemberIds.size();
        int minPersonalWorkCount = command.weekSchedule().getMinPersonalWorkCount();
        int maxPersonalWorkCount = command.weekSchedule().getMaxPersonalWorkCount();

        if (isGloballyImpossible(
            originalTimeDetails,
            workerCount,
            minPersonalWorkCount,
            maxPersonalWorkCount
        )) {
            return new ScheduleCandidateGenerationResult(ALGORITHM_VERSION, List.of());
        }

        long[] workerMemberIds = toLongArray(sortedWorkerMemberIds);
        long allWorkerMask = createAllWorkerMask(workerCount);

        Slot[] slots = createSlots(
            originalTimeDetails,
            workerMemberIds,
            command.unavailableWorkerIdsByTimeDetailId()
        );

        for (Slot slot : slots) {
            if (Long.bitCount(slot.availableWorkerMask()) < slot.requiredCount()) {
                return new ScheduleCandidateGenerationResult(ALGORITHM_VERSION, List.of());
            }
        }

        SearchContext context = createSearchContext(
            originalTimeDetails,
            slots,
            workerMemberIds,
            allWorkerMask,
            minPersonalWorkCount,
            maxPersonalWorkCount
        );

        if (!canStillSatisfyAllConstraints(context)) {
            return new ScheduleCandidateGenerationResult(ALGORITHM_VERSION, List.of());
        }

        search(0, context);

        List<ScheduleCandidate> sortedCandidates = new ArrayList<>(context.topCandidates);
        sortedCandidates.sort(Comparator.comparingInt(ScheduleCandidate::score).reversed());

        List<ScheduleCandidate> numberedCandidates = new ArrayList<>(sortedCandidates.size());
        for (int index = 0; index < sortedCandidates.size(); index++) {
            numberedCandidates.add(sortedCandidates.get(index).withCandidateNo(index + 1));
        }

        return new ScheduleCandidateGenerationResult(ALGORITHM_VERSION, numberedCandidates);
    }

    /**
     * Dynamic MRV 기반 DFS.
     */
    private void search(int depth, SearchContext context) {
        if (!consumeSearchNode(context)) {
            return;
        }

        if (depth == context.slots.length) {
            if (satisfiesMinimumWorkCount(context)) {
                addCandidateBounded(createCandidate(context), context);
            }
            return;
        }

        if (!canStillSatisfyAllConstraints(context)) {
            return;
        }

        int slotIndex = selectNextSlotIndex(context);
        if (slotIndex < 0) {
            return;
        }

        Slot slot = context.slots[slotIndex];

        long eligibleMask = slot.availableWorkerMask & context.notMaxedWorkerMask;
        int eligibleCount = Long.bitCount(eligibleMask);

        if (eligibleCount < slot.requiredCount) {
            return;
        }

        long forcedMask = collectForcedWorkerMask(slot, context);
        int forcedCount = Long.bitCount(forcedMask);

        if (forcedCount > slot.requiredCount) {
            return;
        }

        long optionalMask = eligibleMask & ~forcedMask;
        int optionalNeed = slot.requiredCount - forcedCount;

        if (Long.bitCount(optionalMask) < optionalNeed) {
            return;
        }

        int[] orderedOptionalWorkerIndexes = collectOrderedWorkerIndexes(optionalMask, context);
        long[] generatedCombinationCount = new long[1];

        selectOptionalCombinationLazily(
            slotIndex,
            forcedMask,
            orderedOptionalWorkerIndexes,
            0,
            optionalNeed,
            0L,
            depth,
            generatedCombinationCount,
            context
        );
    }

    /**
     * 현재 상태에서 가장 제약이 강한 슬롯을 고른다.
     *
     * 우선순위:
     * 1. forced worker 반영 후 가능한 조합 수가 작은 슬롯
     * 2. eligible worker 수가 적은 슬롯
     * 3. requiredCount가 큰 슬롯
     * 4. 원래 timeDetail 순서가 빠른 슬롯
     */
    private int selectNextSlotIndex(SearchContext context) {
        int bestSlotIndex = -1;

        long bestBranchCount = Long.MAX_VALUE;
        int bestEligibleCount = Integer.MAX_VALUE;
        int bestRequiredCount = Integer.MIN_VALUE;
        int bestOriginalIndex = Integer.MAX_VALUE;
        int bestForcedCount = Integer.MIN_VALUE;

        for (int slotIndex = 0; slotIndex < context.slots.length; slotIndex++) {
            if (context.assignedSlots[slotIndex]) {
                continue;
            }

            Slot slot = context.slots[slotIndex];

            long eligibleMask = slot.availableWorkerMask & context.notMaxedWorkerMask;
            int eligibleCount = Long.bitCount(eligibleMask);

            if (eligibleCount < slot.requiredCount) {
                return -1;
            }

            long forcedMask = collectForcedWorkerMask(slot, context);
            int forcedCount = Long.bitCount(forcedMask);

            if (forcedCount > slot.requiredCount) {
                return -1;
            }

            long branchCount = combinationCountCapped(
                eligibleCount - forcedCount,
                slot.requiredCount - forcedCount,
                COMBINATION_COUNT_CAP
            );

            boolean better = branchCount < bestBranchCount
                || branchCount == bestBranchCount && forcedCount > bestForcedCount
                || branchCount == bestBranchCount && forcedCount == bestForcedCount && eligibleCount < bestEligibleCount
                || branchCount == bestBranchCount && forcedCount == bestForcedCount
                && eligibleCount == bestEligibleCount && slot.requiredCount > bestRequiredCount
                || branchCount == bestBranchCount && forcedCount == bestForcedCount
                && eligibleCount == bestEligibleCount && slot.requiredCount == bestRequiredCount
                && slot.originalIndex < bestOriginalIndex;

            if (better) {
                bestSlotIndex = slotIndex;
                bestBranchCount = branchCount;
                bestEligibleCount = eligibleCount;
                bestRequiredCount = slot.requiredCount;
                bestOriginalIndex = slot.originalIndex;
                bestForcedCount = forcedCount;
            }
        }

        return bestSlotIndex;
    }

    /**
     * 현재 슬롯에 반드시 포함되어야 하는 근무자 집합을 찾는다.
     *
     * 조건:
     * deficit = minPersonalWorkCount - currentWorkCount
     * remainingAssignableCount == deficit
     *
     * 즉, 이 근무자는 앞으로 가능한 모든 슬롯에 들어가야 최소 근무 횟수를 만족한다.
     */
    private long collectForcedWorkerMask(Slot slot, SearchContext context) {
        long forcedMask = 0L;

        long candidateMask = slot.availableWorkerMask & context.notMaxedWorkerMask;

        while (candidateMask != 0L) {
            long workerBit = candidateMask & -candidateMask;
            int workerIndex = Long.numberOfTrailingZeros(workerBit);

            int deficit = context.minPersonalWorkCount - context.workCounts[workerIndex];

            if (deficit > 0 && deficit == context.remainingAssignableCountByWorker[workerIndex]) {
                forcedMask |= workerBit;
            }

            candidateMask -= workerBit;
        }

        return forcedMask;
    }

    /**
     * forced worker를 제외한 optional worker 조합을 lazy하게 생성한다.
     */
    private void selectOptionalCombinationLazily(
        int slotIndex,
        long forcedMask,
        int[] orderedOptionalWorkerIndexes,
        int start,
        int need,
        long selectedOptionalMask,
        int depth,
        long[] generatedCombinationCount,
        SearchContext context
    ) {
        if (!consumeSearchNode(context)) {
            return;
        }

        if (orderedOptionalWorkerIndexes.length - start < need) {
            return;
        }

        if (need == 0) {
            generatedCombinationCount[0]++;

            long selectedWorkerMask = forcedMask | selectedOptionalMask;

            applySlotSelection(slotIndex, selectedWorkerMask, context);

            if (canStillSatisfyAllConstraints(context)) {
                search(depth + 1, context);
            }

            rollbackSlotSelection(slotIndex, selectedWorkerMask, context);
            return;
        }

        for (int index = start; index < orderedOptionalWorkerIndexes.length; index++) {
            if (context.visitedNodeCount >= MAX_SEARCH_NODE_COUNT) {
                return;
            }

            int workerIndex = orderedOptionalWorkerIndexes[index];
            long workerBit = bit(workerIndex);

            selectOptionalCombinationLazily(
                slotIndex,
                forcedMask,
                orderedOptionalWorkerIndexes,
                index + 1,
                need - 1,
                selectedOptionalMask | workerBit,
                depth,
                generatedCombinationCount,
                context
            );
        }
    }

    /**
     * 슬롯 배정을 현재 탐색 상태에 반영한다.
     */
    private void applySlotSelection(int slotIndex, long selectedWorkerMask, SearchContext context) {
        Slot slot = context.slots[slotIndex];

        context.assignedSlots[slotIndex] = true;
        context.remainingRequiredCount -= slot.requiredCount;
        context.selectedWorkerMaskByOriginalIndex[slot.originalIndex] = selectedWorkerMask;

        long availableMask = slot.availableWorkerMask;
        while (availableMask != 0L) {
            long workerBit = availableMask & -availableMask;
            int workerIndex = Long.numberOfTrailingZeros(workerBit);
            context.remainingAssignableCountByWorker[workerIndex]--;
            availableMask -= workerBit;
        }

        long selectedMask = selectedWorkerMask;
        while (selectedMask != 0L) {
            long workerBit = selectedMask & -selectedMask;
            int workerIndex = Long.numberOfTrailingZeros(workerBit);

            context.workCounts[workerIndex]++;

            if (context.workCounts[workerIndex] >= context.maxPersonalWorkCount) {
                context.notMaxedWorkerMask &= ~workerBit;
            }

            selectedMask -= workerBit;
        }
    }

    /**
     * 슬롯 배정을 이전 상태로 되돌린다.
     */
    private void rollbackSlotSelection(int slotIndex, long selectedWorkerMask, SearchContext context) {
        Slot slot = context.slots[slotIndex];

        long selectedMask = selectedWorkerMask;
        while (selectedMask != 0L) {
            long workerBit = selectedMask & -selectedMask;
            int workerIndex = Long.numberOfTrailingZeros(workerBit);

            if (context.workCounts[workerIndex] >= context.maxPersonalWorkCount) {
                context.notMaxedWorkerMask |= workerBit;
            }

            context.workCounts[workerIndex]--;

            selectedMask -= workerBit;
        }

        long availableMask = slot.availableWorkerMask;
        while (availableMask != 0L) {
            long workerBit = availableMask & -availableMask;
            int workerIndex = Long.numberOfTrailingZeros(workerBit);
            context.remainingAssignableCountByWorker[workerIndex]++;
            availableMask -= workerBit;
        }

        context.selectedWorkerMaskByOriginalIndex[slot.originalIndex] = 0L;
        context.remainingRequiredCount += slot.requiredCount;
        context.assignedSlots[slotIndex] = false;
    }

    /**
     * 남은 슬롯으로 최소/최대 근무 횟수 조건을 만족할 수 있는지 검사한다.
     */
    private boolean canStillSatisfyAllConstraints(SearchContext context) {
        int totalMinimumDeficit = 0;
        int totalMaximumCapacity = 0;

        for (int workerIndex = 0; workerIndex < context.workerMemberIds.length; workerIndex++) {
            int currentWorkCount = context.workCounts[workerIndex];
            int remainingAssignableCount = context.remainingAssignableCountByWorker[workerIndex];

            if (currentWorkCount + remainingAssignableCount < context.minPersonalWorkCount) {
                return false;
            }

            int minimumDeficit = Math.max(0, context.minPersonalWorkCount - currentWorkCount);
            totalMinimumDeficit += minimumDeficit;

            int remainingMaxCapacity = context.maxPersonalWorkCount - currentWorkCount;
            totalMaximumCapacity += Math.max(0, Math.min(remainingMaxCapacity, remainingAssignableCount));
        }

        if (totalMinimumDeficit > context.remainingRequiredCount) {
            return false;
        }

        return context.remainingRequiredCount <= totalMaximumCapacity;
    }

    private boolean satisfiesMinimumWorkCount(SearchContext context) {
        for (int count : context.workCounts) {
            if (count < context.minPersonalWorkCount) {
                return false;
            }
        }

        return true;
    }

    /**
     * optional worker들을 우선순위에 따라 정렬한다.
     *
     * 우선순위:
     * 1. 최소 근무 횟수까지 부족한 정도가 큰 사람
     * 2. 앞으로 배정 가능한 슬롯이 적은 사람
     * 3. 현재 근무 횟수가 적은 사람
     * 4. memberId가 작은 사람
     */
    private int[] collectOrderedWorkerIndexes(long workerMask, SearchContext context) {
        int[] result = new int[Long.bitCount(workerMask)];
        int count = 0;

        long mask = workerMask;
        while (mask != 0L) {
            long workerBit = mask & -mask;
            result[count++] = Long.numberOfTrailingZeros(workerBit);
            mask -= workerBit;
        }

        insertionSortByWorkerPriority(result, context);

        return result;
    }

    private void insertionSortByWorkerPriority(int[] workerIndexes, SearchContext context) {
        for (int i = 1; i < workerIndexes.length; i++) {
            int target = workerIndexes[i];
            int j = i - 1;

            while (j >= 0 && compareWorkerPriority(target, workerIndexes[j], context) < 0) {
                workerIndexes[j + 1] = workerIndexes[j];
                j--;
            }

            workerIndexes[j + 1] = target;
        }
    }

    private int compareWorkerPriority(int leftWorkerIndex, int rightWorkerIndex, SearchContext context) {
        int leftDeficit = Math.max(0, context.minPersonalWorkCount - context.workCounts[leftWorkerIndex]);
        int rightDeficit = Math.max(0, context.minPersonalWorkCount - context.workCounts[rightWorkerIndex]);

        if (leftDeficit != rightDeficit) {
            return Integer.compare(rightDeficit, leftDeficit);
        }

        int leftScarcity = context.remainingAssignableCountByWorker[leftWorkerIndex];
        int rightScarcity = context.remainingAssignableCountByWorker[rightWorkerIndex];

        if (leftScarcity != rightScarcity) {
            return Integer.compare(leftScarcity, rightScarcity);
        }

        int leftWorkCount = context.workCounts[leftWorkerIndex];
        int rightWorkCount = context.workCounts[rightWorkerIndex];

        if (leftWorkCount != rightWorkCount) {
            return Integer.compare(leftWorkCount, rightWorkCount);
        }

        return Long.compare(context.workerMemberIds[leftWorkerIndex], context.workerMemberIds[rightWorkerIndex]);
    }

    /**
     * score 기반 Top-N 유지.
     *
     * PriorityQueue는 가장 낮은 score 후보를 루트에 둔다.
     * 새 후보가 더 좋으면 최악 후보를 제거하고 교체한다.
     */
    private void addCandidateBounded(ScheduleCandidate candidate, SearchContext context) {
        if (context.topCandidates.size() < MAX_CANDIDATE_COUNT) {
            context.topCandidates.offer(candidate);
            return;
        }

        ScheduleCandidate worstCandidate = context.topCandidates.peek();
        if (worstCandidate != null && candidate.score() > worstCandidate.score()) {
            context.topCandidates.poll();
            context.topCandidates.offer(candidate);
        }
    }

    private ScheduleCandidate createCandidate(SearchContext context) {
        Map<Long, List<ScheduleCandidateTimeDetail>> timeDetailsByDayId = new LinkedHashMap<>();

        for (int originalIndex = 0; originalIndex < context.originalTimeDetails.size(); originalIndex++) {
            long selectedWorkerMask = context.selectedWorkerMaskByOriginalIndex[originalIndex];

            if (selectedWorkerMask == 0L) {
                continue;
            }

            TimeDetail timeDetail = context.originalTimeDetails.get(originalIndex);

            List<Long> selectedWorkerMemberIds = toWorkerMemberIdList(
                selectedWorkerMask,
                context.workerMemberIds
            );

            timeDetailsByDayId
                .computeIfAbsent(timeDetail.getDay().getId(), ignored -> new ArrayList<>())
                .add(new ScheduleCandidateTimeDetail(timeDetail.getId(), selectedWorkerMemberIds));
        }

        List<ScheduleCandidateDay> days = new ArrayList<>(timeDetailsByDayId.size());
        for (Map.Entry<Long, List<ScheduleCandidateTimeDetail>> entry : timeDetailsByDayId.entrySet()) {
            days.add(new ScheduleCandidateDay(entry.getKey(), entry.getValue()));
        }

        int score = calculateScore(context);

        return new ScheduleCandidate(0, score, days);
    }

    /**
     * 후보 점수 계산.
     *
     * 반영 기준:
     * 1. 근무 횟수 편차가 작을수록 좋음
     * 2. 최소/최대 근무 횟수 경계에 딱 붙어 있지 않을수록 좋음
     * 3. 특정 근무자에게 몰리지 않을수록 좋음
     * 4. 같은 날 안에서 근무자 구성이 덜 요동칠수록 보기 쉬움
     */
    private int calculateScore(SearchContext context) {
        int workerCount = context.workerMemberIds.length;

        int totalWorkCount = 0;
        int minWorkCount = Integer.MAX_VALUE;
        int maxWorkCount = Integer.MIN_VALUE;

        for (int count : context.workCounts) {
            totalWorkCount += count;
            minWorkCount = Math.min(minWorkCount, count);
            maxWorkCount = Math.max(maxWorkCount, count);
        }

        int rangePenalty = maxWorkCount - minWorkCount;

        int l1DeviationPenalty = 0;
        for (int count : context.workCounts) {
            l1DeviationPenalty += Math.abs(count * workerCount - totalWorkCount);
        }

        int boundaryPenalty = 0;
        for (int count : context.workCounts) {
            int lowerSlack = count - context.minPersonalWorkCount;
            int upperSlack = context.maxPersonalWorkCount - count;
            int slack = Math.min(lowerSlack, upperSlack);

            if (slack <= 0) {
                boundaryPenalty += 3;
            } else if (slack == 1) {
                boundaryPenalty += 1;
            }
        }

        int readabilityPenalty = calculateReadabilityPenalty(context);

        long score = 1_000_000L
            - rangePenalty * 50_000L
            - l1DeviationPenalty * 100L
            - boundaryPenalty * 5_000L
            - readabilityPenalty * 300L;

        return (int) Math.max(1L, Math.min(Integer.MAX_VALUE, score));
    }

    /**
     * 사장님이 보기 쉬운 후보를 위한 penalty.
     *
     * 현재 가진 데이터 기준에서는 같은 day 안에서 연속 timeDetail 간 근무자 구성이
     * 크게 바뀌지 않는 후보를 더 읽기 쉬운 후보로 본다.
     */
    private int calculateReadabilityPenalty(SearchContext context) {
        int penalty = 0;

        Long previousDayId = null;
        long previousWorkerMask = 0L;

        for (int originalIndex = 0; originalIndex < context.originalTimeDetails.size(); originalIndex++) {
            TimeDetail timeDetail = context.originalTimeDetails.get(originalIndex);
            Long currentDayId = timeDetail.getDay().getId();
            long currentWorkerMask = context.selectedWorkerMaskByOriginalIndex[originalIndex];

            if (currentWorkerMask == 0L) {
                continue;
            }

            if (previousDayId != null && previousDayId.equals(currentDayId)) {
                penalty += Long.bitCount(previousWorkerMask ^ currentWorkerMask);
            }

            previousDayId = currentDayId;
            previousWorkerMask = currentWorkerMask;
        }

        return penalty;
    }

    private List<Long> toWorkerMemberIdList(long workerMask, long[] workerMemberIds) {
        List<Long> result = new ArrayList<>(Long.bitCount(workerMask));

        long mask = workerMask;
        while (mask != 0L) {
            long workerBit = mask & -mask;
            int workerIndex = Long.numberOfTrailingZeros(workerBit);
            result.add(workerMemberIds[workerIndex]);
            mask -= workerBit;
        }

        return result;
    }

    private SearchContext createSearchContext(
        List<TimeDetail> originalTimeDetails,
        Slot[] slots,
        long[] workerMemberIds,
        long allWorkerMask,
        int minPersonalWorkCount,
        int maxPersonalWorkCount
    ) {
        int[] remainingAssignableCountByWorker = new int[workerMemberIds.length];
        int remainingRequiredCount = 0;

        for (Slot slot : slots) {
            remainingRequiredCount += slot.requiredCount;

            long availableMask = slot.availableWorkerMask;
            while (availableMask != 0L) {
                long workerBit = availableMask & -availableMask;
                int workerIndex = Long.numberOfTrailingZeros(workerBit);
                remainingAssignableCountByWorker[workerIndex]++;
                availableMask -= workerBit;
            }
        }

        return new SearchContext(
            originalTimeDetails,
            slots,
            workerMemberIds,
            allWorkerMask,
            minPersonalWorkCount,
            maxPersonalWorkCount,
            new boolean[slots.length],
            remainingAssignableCountByWorker,
            remainingRequiredCount,
            new int[workerMemberIds.length],
            new long[originalTimeDetails.size()]
        );
    }

    private Slot[] createSlots(
        List<TimeDetail> timeDetails,
        long[] workerMemberIds,
        Map<Long, Set<Long>> unavailableWorkerIdsByTimeDetailId
    ) {
        Slot[] slots = new Slot[timeDetails.size()];

        for (int originalIndex = 0; originalIndex < timeDetails.size(); originalIndex++) {
            TimeDetail timeDetail = timeDetails.get(originalIndex);
            Set<Long> unavailableMemberIds = unavailableWorkerIdsByTimeDetailId.getOrDefault(
                timeDetail.getId(),
                Set.of()
            );

            long availableWorkerMask = 0L;

            for (int workerIndex = 0; workerIndex < workerMemberIds.length; workerIndex++) {
                if (!unavailableMemberIds.contains(workerMemberIds[workerIndex])) {
                    availableWorkerMask |= bit(workerIndex);
                }
            }

            slots[originalIndex] = new Slot(
                timeDetail,
                timeDetail.getWorkerCount(),
                availableWorkerMask,
                originalIndex
            );
        }

        return slots;
    }

    private boolean isGloballyImpossible(
        List<TimeDetail> timeDetails,
        int workerCount,
        int minPersonalWorkCount,
        int maxPersonalWorkCount
    ) {
        if (workerCount == 0) {
            return !timeDetails.isEmpty();
        }

        if (minPersonalWorkCount > maxPersonalWorkCount) {
            return true;
        }

        int totalRequiredWorkCount = 0;
        for (TimeDetail timeDetail : timeDetails) {
            totalRequiredWorkCount += timeDetail.getWorkerCount();
        }

        int totalMinimumRequired = workerCount * minPersonalWorkCount;
        int totalMaximumAllowed = workerCount * maxPersonalWorkCount;

        if (totalRequiredWorkCount < totalMinimumRequired) {
            return true;
        }

        return totalRequiredWorkCount > totalMaximumAllowed;
    }

    private long[] toLongArray(List<Long> workerMemberIds) {
        long[] result = new long[workerMemberIds.size()];

        for (int index = 0; index < workerMemberIds.size(); index++) {
            result[index] = workerMemberIds.get(index);
        }

        return result;
    }

    private long createAllWorkerMask(int workerCount) {
        if (workerCount == Long.SIZE) {
            return -1L;
        }

        return (1L << workerCount) - 1L;
    }

    private long combinationCountCapped(int n, int r, long cap) {
        if (r < 0 || n < r) {
            return 0L;
        }

        r = Math.min(r, n - r);

        long result = 1L;

        for (int i = 1; i <= r; i++) {
            result = result * (n - r + i) / i;

            if (result > cap) {
                return cap;
            }
        }

        return result;
    }

    private boolean consumeSearchNode(SearchContext context) {
        if (context.visitedNodeCount >= MAX_SEARCH_NODE_COUNT) {
            return false;
        }

        context.visitedNodeCount++;
        return true;
    }

    private long bit(int workerIndex) {
        return 1L << workerIndex;
    }

    private record Slot(
        TimeDetail timeDetail,
        int requiredCount,
        long availableWorkerMask,
        int originalIndex
    ) {
    }

    private static final class SearchContext {

        private final List<TimeDetail> originalTimeDetails;
        private final Slot[] slots;
        private final long[] workerMemberIds;
        private long notMaxedWorkerMask;
        private final int minPersonalWorkCount;
        private final int maxPersonalWorkCount;

        private final boolean[] assignedSlots;
        private final int[] remainingAssignableCountByWorker;
        private int remainingRequiredCount;

        private final int[] workCounts;
        private final long[] selectedWorkerMaskByOriginalIndex;

        private final PriorityQueue<ScheduleCandidate> topCandidates =
            new PriorityQueue<>(Comparator.comparingInt(ScheduleCandidate::score));

        private long visitedNodeCount;

        private SearchContext(
            List<TimeDetail> originalTimeDetails,
            Slot[] slots,
            long[] workerMemberIds,
            long notMaxedWorkerMask,
            int minPersonalWorkCount,
            int maxPersonalWorkCount,
            boolean[] assignedSlots,
            int[] remainingAssignableCountByWorker,
            int remainingRequiredCount,
            int[] workCounts,
            long[] selectedWorkerMaskByOriginalIndex
        ) {
            this.originalTimeDetails = originalTimeDetails;
            this.slots = slots;
            this.workerMemberIds = workerMemberIds;
            this.notMaxedWorkerMask = notMaxedWorkerMask;
            this.minPersonalWorkCount = minPersonalWorkCount;
            this.maxPersonalWorkCount = maxPersonalWorkCount;
            this.assignedSlots = assignedSlots;
            this.remainingAssignableCountByWorker = remainingAssignableCountByWorker;
            this.remainingRequiredCount = remainingRequiredCount;
            this.workCounts = workCounts;
            this.selectedWorkerMaskByOriginalIndex = selectedWorkerMaskByOriginalIndex;
        }
    }
}
