package com.autoschedule.workerselect.dto;

import java.util.List;

/**
 * 근무 불가 타임 제출 결과를 반환한다.
 */
public record WorkerSelectResponse(
        Long workPlaceId,
        Long memberId,
        List<WorkerSelectTimeDetailResponse> timeDetails
) {

    /**
     * 근무 불가 타임 제출 응답을 생성한다.
     */
    public static WorkerSelectResponse of(
            Long workPlaceId,
            Long memberId,
            List<WorkerSelectTimeDetailResponse> timeDetails
    ) {
        return new WorkerSelectResponse(
                workPlaceId,
                memberId,
                timeDetails
        );
    }
}