package com.autoschedule.crew.controller;

import com.autoschedule.auth.jwt.JwtAuthenticationPrincipal;
import com.autoschedule.crew.dto.CrewListResponse;
import com.autoschedule.crew.service.CrewManagementService;
import com.autoschedule.global.security.annotation.OwnerOnly;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 사업장 근무자 관리 API 요청을 처리한다.
 */
@RestController
@RequiredArgsConstructor
public class CrewManagementController {

    private final CrewManagementService crewManagementService;

    /**
     * 로그인 회원의 권한에 맞는 사업장 근무자 목록을 조회한다.
     */
    @GetMapping("/api/work-places/{workPlaceId}/crews")
    public CrewListResponse getWorkerCrews(
            @AuthenticationPrincipal JwtAuthenticationPrincipal principal,
            @PathVariable Long workPlaceId
    ) {
        return crewManagementService.getWorkerCrews(principal.memberId(), workPlaceId);
    }

    /**
     * 사장님이 본인 사업장의 근무자 크루를 삭제한다.
     */
    @OwnerOnly
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @DeleteMapping("/api/work-places/{workPlaceId}/crews/{crewId}")
    public void deleteWorkerCrew(
            @AuthenticationPrincipal JwtAuthenticationPrincipal principal,
            @PathVariable Long workPlaceId,
            @PathVariable Long crewId
    ) {
        crewManagementService.deleteWorkerCrew(principal.memberId(), workPlaceId, crewId);
    }
}
