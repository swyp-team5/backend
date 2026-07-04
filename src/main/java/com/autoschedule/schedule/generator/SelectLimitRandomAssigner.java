package com.autoschedule.schedule.generator;

import com.autoschedule.schedulecondition.domain.TimeDetail;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 선택제한(selectLimit) time_detail에 대해 랜덤 근무자 배정을 수행하는 모듈.
 *
 * 선택제한 일자는 직원이 자발적으로 신청한 슬롯이므로
 * 근무 불가 조건, min/max 제약 모두 적용하지 않는다.
 * 근무자 수는 항상 필요 인원을 충족하므로 배정 실패 케이스는 존재하지 않는다.
 * workCounts에도 반영하지 않아 이후 일반 슬롯 탐색과 완전히 독립적으로 동작한다.
 *
 * time_detail마다 독립적으로 shuffle하여 특정 근무자에게 배정이 편중되지 않도록 한다.
 */
public class SelectLimitRandomAssigner {

    /**
     * 선택제한 time_detail에 대해 랜덤 배정을 수행한다.
     *
     * time_detail마다 근무자 순서를 독립적으로 shuffle하여
     * 배정이 균등하게 분산되도록 한다.
     *
     * @param selectLimitTimeDetails  배정 대상 선택제한 time_detail 목록
     * @param originalTimeDetails     전체 time_detail 목록 (originalIndex 산출에 사용)
     * @param workerMemberIds         전체 근무자 ID 배열 (workerIndex 기준)
     * @param memberIdToWorkerIndex   memberId → workerIndex 역방향 조회 맵
     * @return originalTimeDetails 인덱스 기준 배정 결과 bit mask 배열
     */
    public long[] assign(
            List<TimeDetail> selectLimitTimeDetails,
            List<TimeDetail> originalTimeDetails,
            long[] workerMemberIds,
            Map<Long, Integer> memberIdToWorkerIndex
    ) {
        long[] assignedMasks = new long[originalTimeDetails.size()];
        Map<Long, Integer> tdIdToOriginalIndex = buildTdIdToOriginalIndex(originalTimeDetails);

        // 재사용할 근무자 목록 (time_detail마다 shuffle)
        List<Long> workerList = new ArrayList<>(workerMemberIds.length);
        for (long id : workerMemberIds) workerList.add(id);

        for (TimeDetail td : selectLimitTimeDetails) {
            // time_detail마다 독립적으로 shuffle → 배정 편중 방지
            Collections.shuffle(workerList);

            long assignedMask = 0L;
            int count = 0;

            for (Long memberId : workerList) {
                assignedMask |= (1L << memberIdToWorkerIndex.get(memberId));
                count++;
                if (count == td.getWorkerCount()) break;
            }

            assignedMasks[tdIdToOriginalIndex.get(td.getId())] = assignedMask;
        }

        return assignedMasks;
    }

    private Map<Long, Integer> buildTdIdToOriginalIndex(List<TimeDetail> originalTimeDetails) {
        Map<Long, Integer> map = new HashMap<>();
        for (int i = 0; i < originalTimeDetails.size(); i++) {
            map.put(originalTimeDetails.get(i).getId(), i);
        }
        return map;
    }
}