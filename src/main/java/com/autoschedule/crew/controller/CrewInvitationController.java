package com.autoschedule.crew.controller;

import com.autoschedule.auth.jwt.JwtAuthenticationPrincipal;
import com.autoschedule.crew.dto.CrewInvitationAcceptResponse;
import com.autoschedule.crew.dto.CrewInvitationCreateResponse;
import com.autoschedule.crew.service.CrewInvitationService;
import com.autoschedule.global.security.annotation.OwnerOnly;
import com.autoschedule.global.security.annotation.WorkerOnly;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 사업장 크루 초대 코드 생성과 수락 API를 제공한다.
 */
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
