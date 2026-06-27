package com.autoschedule.workplace.dto;

import com.autoschedule.crew.domain.Crew;
import com.autoschedule.crew.domain.CrewJoinStatus;
import com.autoschedule.crew.domain.CrewRole;
import com.autoschedule.crew.domain.CrewStatus;
import com.autoschedule.workplace.domain.WorkPlace;
import com.autoschedule.workplace.domain.WorkPlaceSize;
import com.autoschedule.workplace.domain.WorkPlaceStatus;

/**
 * 홈 화면 매장 선택 목록의 단건 응답을 표현한다.
 */
public record MyWorkPlaceResponse(
        Long workPlaceId,
        String name,
        WorkPlaceSize size,
        String roadAddress,
        String detailAddress,
        Long ownerMemberId,
        Long crewId,
        CrewRole crewRole,
        CrewJoinStatus joinStatus,
        CrewStatus crewStatus,
        WorkPlaceStatus workPlaceStatus
) {

    /**
     * 승인된 활성 크루 소속과 사업장 정보를 응답으로 변환한다.
     */
    public static MyWorkPlaceResponse from(Crew crew) {
        WorkPlace workPlace = crew.getWorkPlace();
        return new MyWorkPlaceResponse(
                workPlace.getId(),
                workPlace.getName(),
                workPlace.getSize(),
                workPlace.getRoadAddress(),
                workPlace.getDetailAddress(),
                workPlace.getOwnerMemberId(),
                crew.getId(),
                crew.getCrewRole(),
                crew.getJoinStatus(),
                crew.getStatus(),
                workPlace.getStatus()
        );
    }
}
