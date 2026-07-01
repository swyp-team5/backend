package com.autoschedule.crew.dto;

import com.autoschedule.crew.domain.Crew;
import com.autoschedule.crew.domain.CrewJoinStatus;
import com.autoschedule.crew.domain.CrewRole;
import com.autoschedule.crew.domain.CrewStatus;
import java.time.LocalDateTime;

/**
 * 사장님에게 노출되는 사업장 근무자 상세 정보 응답이다.
 */
public record OwnerCrewMemberResponse(
        Long crewId,
        Long memberId,
        String name,
        String phoneNumber,
        String profileImageUrl,
        CrewRole crewRole,
        CrewJoinStatus joinStatus,
        CrewStatus crewStatus,
        LocalDateTime createdAt
) implements CrewMemberResponse {

    /**
     * 크루와 프로필 이미지 URL을 사장님 전용 응답으로 변환한다.
     */
    public static OwnerCrewMemberResponse from(Crew crew, String profileImageUrl) {
        return new OwnerCrewMemberResponse(
                crew.getId(),
                crew.getMember().getId(),
                crew.getMember().getName(),
                crew.getMember().getPhoneNumber(),
                profileImageUrl,
                crew.getCrewRole(),
                crew.getJoinStatus(),
                crew.getStatus(),
                crew.getCreatedAt()
        );
    }
}
