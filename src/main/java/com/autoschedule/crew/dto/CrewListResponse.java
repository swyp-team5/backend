package com.autoschedule.crew.dto;

import java.util.List;

/**
 * 사업장 근무자 목록 조회 응답을 담는다.
 */
public record CrewListResponse(
        List<? extends CrewMemberResponse> crews
) {
}
