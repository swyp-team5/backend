package com.autoschedule.schedule.generator;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 유일성과 파트 배정 공정성, 후보 간 다양성을 기준으로 최종 후보를 선별한다.
 */
final class ScheduleCandidateSelector {

    private static final Comparator<CandidateEntry> STABLE_TIE_BREAKER =
        Comparator.comparingInt((CandidateEntry entry) -> entry.candidate().score()).reversed()
            .thenComparingInt(entry -> entry.candidate().candidateNo());

    /**
     * 공정성이 좋은 그룹부터 처리하고 같은 그룹에서는 서로 다른 후보를 우선한다.
     */
    List<ScheduleCandidate> select(
        List<ScheduleCandidate> candidates,
        List<Long> workerMemberIds,
        int limit
    ) {
        if (limit <= 0 || candidates.isEmpty()) {
            return List.of();
        }

        Map<ScheduleCandidateSignature, ScheduleCandidate> uniqueCandidates = deduplicate(candidates);
        Map<ScheduleCandidateQuality, List<CandidateEntry>> entriesByQuality = new TreeMap<>();

        for (Map.Entry<ScheduleCandidateSignature, ScheduleCandidate> entry : uniqueCandidates.entrySet()) {
            ScheduleCandidateQuality quality = ScheduleCandidateQuality.from(entry.getValue(), workerMemberIds);
            entriesByQuality
                .computeIfAbsent(quality, ignored -> new ArrayList<>())
                .add(new CandidateEntry(entry.getValue(), entry.getKey()));
        }

        List<CandidateEntry> selectedEntries = new ArrayList<>(Math.min(limit, uniqueCandidates.size()));
        for (List<CandidateEntry> sameQualityEntries : entriesByQuality.values()) {
            selectDiverseEntries(sameQualityEntries, selectedEntries, limit);
            if (selectedEntries.size() >= limit) {
                break;
            }
        }

        List<ScheduleCandidate> selectedCandidates = new ArrayList<>(selectedEntries.stream()
            .map(CandidateEntry::candidate)
            .toList());
        selectedCandidates.sort(
            Comparator.comparing((ScheduleCandidate candidate) ->
                    ScheduleCandidateQuality.from(candidate, workerMemberIds))
                .thenComparing(Comparator.comparingInt(ScheduleCandidate::score).reversed())
                .thenComparingInt(ScheduleCandidate::candidateNo)
        );
        return selectedCandidates;
    }

    /**
     * 동일한 실제 배정은 기존 점수가 높은 한 건만 유지한다.
     */
    private Map<ScheduleCandidateSignature, ScheduleCandidate> deduplicate(
        List<ScheduleCandidate> candidates
    ) {
        Map<ScheduleCandidateSignature, ScheduleCandidate> uniqueCandidates = new LinkedHashMap<>();

        for (ScheduleCandidate candidate : candidates) {
            ScheduleCandidateSignature signature = ScheduleCandidateSignature.from(candidate);
            uniqueCandidates.merge(
                signature,
                candidate,
                (existing, incoming) -> incoming.score() > existing.score() ? incoming : existing
            );
        }

        return uniqueCandidates;
    }

    /**
     * 같은 공정성 그룹에서는 이미 선택된 후보와의 최소 거리가 가장 큰 후보를 고른다.
     */
    private void selectDiverseEntries(
        List<CandidateEntry> remainingEntries,
        List<CandidateEntry> selectedEntries,
        int limit
    ) {
        List<CandidateEntry> remaining = new ArrayList<>(remainingEntries);
        Map<CandidateEntry, Integer> minimumDistanceByEntry = new HashMap<>();

        if (!selectedEntries.isEmpty()) {
            remaining.forEach(entry -> minimumDistanceByEntry.put(
                entry,
                minimumDistance(entry, selectedEntries)
            ));
        }

        while (!remaining.isEmpty() && selectedEntries.size() < limit) {
            CandidateEntry next = selectedEntries.isEmpty()
                ? remaining.stream().min(STABLE_TIE_BREAKER).orElseThrow()
                : remaining.stream()
                    .max(Comparator
                        .comparingInt((CandidateEntry entry) -> minimumDistanceByEntry.get(entry))
                        .thenComparing(STABLE_TIE_BREAKER.reversed()))
                    .orElseThrow();

            selectedEntries.add(next);
            remaining.remove(next);
            minimumDistanceByEntry.remove(next);

            for (CandidateEntry remainingEntry : remaining) {
                int distanceToNewSelection = remainingEntry.signature().distanceTo(next.signature());
                minimumDistanceByEntry.merge(
                    remainingEntry,
                    distanceToNewSelection,
                    Math::min
                );
            }
        }
    }

    /**
     * 아직 선택된 후보가 없으면 모든 후보를 같은 거리로 취급한다.
     */
    private int minimumDistance(CandidateEntry candidate, List<CandidateEntry> selectedEntries) {
        if (selectedEntries.isEmpty()) {
            return 0;
        }

        int minimumDistance = Integer.MAX_VALUE;
        for (CandidateEntry selectedEntry : selectedEntries) {
            minimumDistance = Math.min(
                minimumDistance,
                candidate.signature().distanceTo(selectedEntry.signature())
            );
        }
        return minimumDistance;
    }

    /**
     * 후보와 정규화 배정 키를 함께 보관한다.
     */
    private record CandidateEntry(
        ScheduleCandidate candidate,
        ScheduleCandidateSignature signature
    ) {
    }
}
