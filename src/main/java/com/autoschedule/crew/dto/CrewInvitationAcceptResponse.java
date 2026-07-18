package com.autoschedule.crew.dto;

import com.autoschedule.crew.domain.Crew;
import com.autoschedule.crew.domain.CrewJoinStatus;
import com.autoschedule.crew.domain.CrewRole;
import com.autoschedule.crew.domain.CrewStatus;

/**
 * 근무자가 초대를 수락한 뒤 생성된 크루 소속 정보를 반환한다.
 */
public record CrewInvitationAcceptResponse(
        Long crewId,
        Long workPlaceId,
        String workPlaceName,
        CrewJoinStatus joinStatus,
        CrewRole crewRole,
        CrewStatus status
) {

    /**
     * 저장된 크루 엔티티를 모바일 응답 DTO로 변환한다.
     */
    public static CrewInvitationAcceptResponse from(Crew crew) {
        return new CrewInvitationAcceptResponse(
                crew.getId(),
                crew.getWorkPlace().getId(),
                crew.getWorkPlace().getName(),
                crew.getJoinStatus(),
                crew.getCrewRole(),
                crew.getStatus()
        );
    }
}
