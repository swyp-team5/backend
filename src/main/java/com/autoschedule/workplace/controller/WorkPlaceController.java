package com.autoschedule.workplace.controller;

import com.autoschedule.auth.jwt.JwtAuthenticationPrincipal;
import com.autoschedule.workplace.dto.MyWorkPlaceListResponse;
import com.autoschedule.workplace.service.WorkPlaceService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 사업장 조회 API 요청을 처리한다.
 */
@RestController
@RequiredArgsConstructor
public class WorkPlaceController {

    private final WorkPlaceService workPlaceService;

    /**
     * 현재 로그인한 회원이 홈 화면에서 선택할 수 있는 사업장 목록을 조회한다.
     */
    @GetMapping("/api/work-places/me")
    public MyWorkPlaceListResponse getMyWorkPlaces(@AuthenticationPrincipal JwtAuthenticationPrincipal principal) {
        return workPlaceService.getMyWorkPlaces(principal.memberId());
    }
}
