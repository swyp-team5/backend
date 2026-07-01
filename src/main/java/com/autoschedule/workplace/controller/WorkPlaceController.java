package com.autoschedule.workplace.controller;

import com.autoschedule.auth.jwt.JwtAuthenticationPrincipal;
import com.autoschedule.global.security.annotation.OwnerOnly;
import com.autoschedule.workplace.dto.MyWorkPlaceListResponse;
import com.autoschedule.workplace.dto.MyWorkPlaceResponse;
import com.autoschedule.workplace.dto.WorkPlaceCreateRequest;
import com.autoschedule.workplace.dto.WorkPlacePhoneNumberUpdateRequest;
import com.autoschedule.workplace.service.WorkPlaceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
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

    /**
     * 사장님이 추가 사업장을 생성하고 본인을 OWNER 크루로 등록한다.
     */
    @OwnerOnly
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping("/api/work-places")
    public MyWorkPlaceResponse createWorkPlace(
            @AuthenticationPrincipal JwtAuthenticationPrincipal principal,
            @Valid @RequestBody WorkPlaceCreateRequest request
    ) {
        return workPlaceService.createAdditionalWorkPlace(principal.memberId(), request);
    }

    /**
     * 사장님이 본인 사업장의 전화번호 부가 정보를 추가, 수정 또는 삭제한다.
     */
    @OwnerOnly
    @PatchMapping("/api/work-places/{workPlaceId}/phone-number")
    public MyWorkPlaceResponse updatePhoneNumber(
            @AuthenticationPrincipal JwtAuthenticationPrincipal principal,
            @PathVariable Long workPlaceId,
            @Valid @RequestBody WorkPlacePhoneNumberUpdateRequest request
    ) {
        return workPlaceService.updatePhoneNumber(principal.memberId(), workPlaceId, request);
    }
}
