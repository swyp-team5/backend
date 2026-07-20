package com.autoschedule.workchange.controller;

import com.autoschedule.auth.jwt.JwtAuthenticationPrincipal;
import com.autoschedule.global.security.annotation.OwnerOnly;
import com.autoschedule.global.security.annotation.WorkerOnly;
import com.autoschedule.workchange.dto.ShiftSwapWorkChangeRequest;
import com.autoschedule.workchange.dto.SubstituteWorkChangeRequest;
import com.autoschedule.workchange.dto.WorkChangeRejectionRequest;
import com.autoschedule.workchange.dto.WorkChangeRequestPageResponse;
import com.autoschedule.workchange.dto.WorkChangeRequestResponse;
import com.autoschedule.workchange.dto.WorkChangeRequestScope;
import com.autoschedule.workchange.service.WorkChangeRequestService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 근무자의 교대/대타 요청 API를 처리한다.
 */
@Validated
@RestController
@RequiredArgsConstructor
public class WorkChangeRequestController {

    private final WorkChangeRequestService workChangeRequestService;

    /**
     * 본인의 확정 근무를 다른 근무자에게 넘기는 대타 요청을 생성한다.
     */
    @WorkerOnly
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping("/api/work-places/{workPlaceId}/work-change-requests/substitute")
    public WorkChangeRequestResponse createSubstituteRequest(
            @AuthenticationPrincipal JwtAuthenticationPrincipal principal,
            @PathVariable Long workPlaceId,
            @Valid @RequestBody SubstituteWorkChangeRequest request
    ) {
        return workChangeRequestService.createSubstituteRequest(principal.memberId(), workPlaceId, request);
    }

    /**
     * 본인의 확정 근무와 대상 근무자의 확정 근무를 바꾸는 교대 요청을 생성한다.
     */
    @WorkerOnly
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping("/api/work-places/{workPlaceId}/work-change-requests/shift-swap")
    public WorkChangeRequestResponse createShiftSwapRequest(
            @AuthenticationPrincipal JwtAuthenticationPrincipal principal,
            @PathVariable Long workPlaceId,
            @Valid @RequestBody ShiftSwapWorkChangeRequest request
    ) {
        return workChangeRequestService.createShiftSwapRequest(principal.memberId(), workPlaceId, request);
    }

    /**
     * 근무자가 자신이 보낸 요청 또는 받은 요청 목록을 조회한다.
     */
    @WorkerOnly
    @GetMapping("/api/work-places/{workPlaceId}/work-change-requests")
    public WorkChangeRequestPageResponse getWorkerRequests(
            @AuthenticationPrincipal JwtAuthenticationPrincipal principal,
            @PathVariable Long workPlaceId,
            @RequestParam(defaultValue = "SENT") WorkChangeRequestScope scope,
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "페이지 번호는 0 이상이어야 합니다.") int page,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "페이지 크기는 1 이상이어야 합니다.")
            @Max(value = 100, message = "페이지 크기는 100 이하여야 합니다.") int size
    ) {
        return workChangeRequestService.getWorkerRequests(principal.memberId(), workPlaceId, scope, page, size);
    }

    /**
     * 사장님이 사업장의 전체 교대/대타 요청 목록을 조회한다.
     */
    @OwnerOnly
    @GetMapping("/api/work-places/{workPlaceId}/owner/work-change-requests")
    public WorkChangeRequestPageResponse getOwnerRequests(
            @AuthenticationPrincipal JwtAuthenticationPrincipal principal,
            @PathVariable Long workPlaceId,
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "페이지 번호는 0 이상이어야 합니다.") int page,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "페이지 크기는 1 이상이어야 합니다.")
            @Max(value = 100, message = "페이지 크기는 100 이하여야 합니다.") int size
    ) {
        return workChangeRequestService.getOwnerRequests(principal.memberId(), workPlaceId, page, size);
    }

    /**
     * 대상 근무자가 교대/대타 요청을 수락한다.
     */
    @WorkerOnly
    @PostMapping("/api/work-places/{workPlaceId}/work-change-requests/{requestId}/accept")
    public WorkChangeRequestResponse acceptByTarget(
            @AuthenticationPrincipal JwtAuthenticationPrincipal principal,
            @PathVariable Long workPlaceId,
            @PathVariable Long requestId
    ) {
        return workChangeRequestService.acceptByTarget(principal.memberId(), workPlaceId, requestId);
    }

    /**
     * 대상 근무자가 교대/대타 요청을 거절한다.
     */
    @WorkerOnly
    @PostMapping("/api/work-places/{workPlaceId}/work-change-requests/{requestId}/reject")
    public WorkChangeRequestResponse rejectByTarget(
            @AuthenticationPrincipal JwtAuthenticationPrincipal principal,
            @PathVariable Long workPlaceId,
            @PathVariable Long requestId,
            @Valid @RequestBody(required = false) WorkChangeRejectionRequest request
    ) {
        return workChangeRequestService.rejectByTarget(
            principal.memberId(),
            workPlaceId,
            requestId,
            request == null ? WorkChangeRejectionRequest.empty() : request);
    }

    /**
     * 요청자가 대상 근무자 응답 전 교대/대타 요청을 취소한다.
     */
    @WorkerOnly
    @DeleteMapping("/api/work-places/{workPlaceId}/work-change-requests/{requestId}")
    public WorkChangeRequestResponse cancelRequest(
            @AuthenticationPrincipal JwtAuthenticationPrincipal principal,
            @PathVariable Long workPlaceId,
            @PathVariable Long requestId
    ) {
        return workChangeRequestService.cancelRequest(principal.memberId(), workPlaceId, requestId);
    }

    /**
     * 사장님이 대상 근무자가 수락한 교대/대타 요청을 최종 승인한다.
     */
    @OwnerOnly
    @PostMapping("/api/work-places/{workPlaceId}/owner/work-change-requests/{requestId}/approve")
    public WorkChangeRequestResponse approveByOwner(
            @AuthenticationPrincipal JwtAuthenticationPrincipal principal,
            @PathVariable Long workPlaceId,
            @PathVariable Long requestId
    ) {
        return workChangeRequestService.approveByOwner(principal.memberId(), workPlaceId, requestId);
    }

    /**
     * 사장님이 대상 근무자가 수락한 교대/대타 요청을 최종 거절한다.
     */
    @OwnerOnly
    @PostMapping("/api/work-places/{workPlaceId}/owner/work-change-requests/{requestId}/reject")
    public WorkChangeRequestResponse rejectByOwner(
            @AuthenticationPrincipal JwtAuthenticationPrincipal principal,
            @PathVariable Long workPlaceId,
            @PathVariable Long requestId,
            @Valid @RequestBody(required = false) WorkChangeRejectionRequest request
    ) {
        return workChangeRequestService.rejectByOwner(
            principal.memberId(),
            workPlaceId,
            requestId,
            request == null ? WorkChangeRejectionRequest.empty() : request);
    }
}
