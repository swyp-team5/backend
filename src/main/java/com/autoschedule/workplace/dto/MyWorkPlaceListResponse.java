package com.autoschedule.workplace.dto;

import java.util.List;

/**
 * 홈 화면 매장 선택 목록 응답을 표현한다.
 */
public record MyWorkPlaceListResponse(
        List<MyWorkPlaceResponse> workPlaces
) {
}
