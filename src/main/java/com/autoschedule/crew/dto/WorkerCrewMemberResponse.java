package com.autoschedule.crew.dto;

import com.autoschedule.crew.domain.Crew;
import com.autoschedule.crew.domain.CrewRole;

/**
 * 근무자에게 노출되는 사업장 근무자 제한 정보 응답이다.
 */
public record WorkerCrewMemberResponse(
        Long crewId,
        Long memberId,
        CrewRole crewRole,
        String name,
        String profileImageUrl
) implements CrewMemberResponse {

    /**
     * 크루와 프로필 이미지 URL을 근무자 전용 응답으로 변환한다.
     */
    public static WorkerCrewMemberResponse from(Crew crew, String profileImageUrl) {
        return new WorkerCrewMemberResponse(
                crew.getId(),
                crew.getMember().getId(),
                crew.getCrewRole(),
                crew.getMember().getName(),
                profileImageUrl
        );
    }
}
