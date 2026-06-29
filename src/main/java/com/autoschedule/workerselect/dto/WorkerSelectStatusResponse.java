package com.autoschedule.workerselect.dto;

import java.util.List;

/**
 * 사업장 근무자들의 근무 불가 제출 여부 목록을 반환한다.
 */
public record WorkerSelectStatusResponse(
        Long workPlaceId,
        Long weekScheduleId,
        List<WorkerSelectMemberStatusResponse> workers
) {

    /**
     * 근무자 제출 여부 목록 응답을 생성한다.
     */
    public static WorkerSelectStatusResponse of(
            Long workPlaceId,
            Long weekScheduleId,
            List<WorkerSelectMemberStatusResponse> workers
    ) {
        return new WorkerSelectStatusResponse(
                workPlaceId,
                weekScheduleId,
                workers
        );
    }
}