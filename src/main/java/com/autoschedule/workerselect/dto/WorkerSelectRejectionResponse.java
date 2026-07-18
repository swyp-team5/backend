package com.autoschedule.workerselect.dto;

/**
 * 근무 불가 제출 반려 결과를 반환한다.
 */
public record WorkerSelectRejectionResponse(
        Long workPlaceId,
        Long weekScheduleId,
        Long memberId
) {

    /**
     * 근무 불가 제출 반려 응답을 생성한다.
     */
    public static WorkerSelectRejectionResponse of(
            Long workPlaceId,
            Long weekScheduleId,
            Long memberId
    ) {
        return new WorkerSelectRejectionResponse(
                workPlaceId,
                weekScheduleId,
                memberId
        );
    }
}
