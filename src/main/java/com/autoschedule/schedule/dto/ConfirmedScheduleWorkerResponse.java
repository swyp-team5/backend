package com.autoschedule.schedule.dto;

import com.autoschedule.member.domain.Member;
import com.autoschedule.schedule.domain.ConfirmedScheduleAssignment;

/**
 * 사장 주간 근무표에 표시할 근무자 요약 응답이다.
 */
public record ConfirmedScheduleWorkerResponse(
        Long assignmentId,
        Long memberId,
        String name,
        String profileImageUrl
) {

    /**
     * 회원 정보와 프로필 이미지 URL을 근무자 요약 응답으로 변환한다.
     */
    public static ConfirmedScheduleWorkerResponse from(
            ConfirmedScheduleAssignment assignment,
            Member member,
            String profileImageUrl
    ) {
        return new ConfirmedScheduleWorkerResponse(
                assignment == null ? null : assignment.getId(),
                member == null ? null : member.getId(),
                member == null ? null : member.getName(),
                profileImageUrl
        );
    }
}
