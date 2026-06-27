package com.autoschedule.workerselect.dto;

import com.autoschedule.crew.domain.Crew;

import java.util.List;

/**
 * 근무자별 근무 불가 제출 여부를 반환한다.
 */
public record WorkerSelectMemberStatusResponse(
        Long memberId,
        String memberName,
        Boolean submitted
) {

    /**
     * 크루 정보와 제출 여부를 응답 DTO로 변환한다.
     */
    public static WorkerSelectMemberStatusResponse from(
            Crew crew,
            List<Long> submittedMemberIds
    ) {
        return new WorkerSelectMemberStatusResponse(
                crew.getMember().getId(),
                crew.getMember().getName(),
                submittedMemberIds.contains(crew.getMember().getId())
        );
    }
}