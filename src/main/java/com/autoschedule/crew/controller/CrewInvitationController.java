package com.autoschedule.crew.controller;

import com.autoschedule.auth.jwt.JwtAuthenticationPrincipal;
import com.autoschedule.crew.dto.CrewInvitationAcceptResponse;
import com.autoschedule.crew.dto.CrewInvitationCreateResponse;
import com.autoschedule.crew.dto.CrewInvitationHistoryResponse;
import com.autoschedule.crew.service.CrewInvitationService;
import com.autoschedule.global.security.annotation.OwnerOnly;
import com.autoschedule.global.security.annotation.WorkerOnly;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 사업장 크루 초대 코드 생성과 수락 API를 제공한다.
 */
@Validated
@RestController
@RequiredArgsConstructor
public class CrewInvitationController {

    private final CrewInvitationService crewInvitationService;

    /**
     * 사장님이 본인 사업장에 사용할 1회용 초대 코드를 생성한다.
     */
    @OwnerOnly
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping("/api/work-places/{workPlaceId}/crew-invitations")
    public CrewInvitationCreateResponse createInvitation(
            @AuthenticationPrincipal JwtAuthenticationPrincipal principal,
            @PathVariable Long workPlaceId
    ) {
        return crewInvitationService.createInvitation(principal.memberId(), workPlaceId);
    }

    /**
     * 사장님이 본인 사업장에서 발급한 초대 코드 이력을 페이지 단위로 조회한다.
     */
    @OwnerOnly
    @GetMapping("/api/work-places/{workPlaceId}/crew-invitations")
    public CrewInvitationHistoryResponse getInvitationHistory(
            @AuthenticationPrincipal JwtAuthenticationPrincipal principal,
            @PathVariable Long workPlaceId,
            @RequestParam(defaultValue = "0")
            @Min(value = 0, message = "페이지 번호는 0 이상이어야 합니다.")
            int page,
            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "페이지 크기는 1 이상이어야 합니다.")
            @Max(value = 100, message = "페이지 크기는 100 이하여야 합니다.")
            int size
    ) {
        return crewInvitationService.getInvitationHistory(principal.memberId(), workPlaceId, page, size);
    }

    /**
     * 근무자가 초대 코드를 수락하고 즉시 사업장 크루로 등록된다.
     */
    @WorkerOnly
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping("/api/crew-invitations/{inviteCode}/accept")
    public CrewInvitationAcceptResponse acceptInvitation(
            @AuthenticationPrincipal JwtAuthenticationPrincipal principal,
            @PathVariable String inviteCode
    ) {
        return crewInvitationService.acceptInvitation(principal.memberId(), inviteCode);
    }
}
