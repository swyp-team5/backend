package com.autoschedule.workerselect.controller;


import com.autoschedule.auth.jwt.JwtAuthenticationPrincipal;
import com.autoschedule.global.security.annotation.OwnerOnly;
import com.autoschedule.workerselect.dto.WorkerSelectStatusResponse;
import org.springframework.validation.annotation.Validated;
import com.autoschedule.global.security.annotation.WorkerOnly;
import com.autoschedule.workerselect.dto.WorkerSelectRequest;
import com.autoschedule.workerselect.dto.WorkerSelectResponse;
import com.autoschedule.workerselect.service.WorkerSelectService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * 근무자 스케줄 조건 선택 API 요청을 처리한다.
 */
@Validated
@RestController
@RequiredArgsConstructor
public class WorkerSelectController {

    private final WorkerSelectService workerSelectService;

    /**
     * 근무자가 근무 불가능한 날짜 및 타임을 제출한다.
     */
    @WorkerOnly
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping("/api/work-places/{workPlaceId}/worker-select")
    public WorkerSelectResponse selectWorkerUnavailable(
            @AuthenticationPrincipal JwtAuthenticationPrincipal principal,
            @PathVariable Long workPlaceId,
            @Valid @RequestBody WorkerSelectRequest request
    ) {
        return workerSelectService.selectWorkerUnavailable(
                principal.memberId(),
                workPlaceId,
                request
        );
    }

    /**
     * 사업장 근무자들의 근무 불가 제출 여부를 조회한다.
     */
    @OwnerOnly
    @GetMapping("/api/work-places/{workPlaceId}/week-schedules/{weekScheduleId}/worker-select/status")
    public WorkerSelectStatusResponse getWorkerSelectStatus(
            @AuthenticationPrincipal JwtAuthenticationPrincipal principal,
            @PathVariable Long workPlaceId,
            @PathVariable Long weekScheduleId
    ) {
        return workerSelectService.getWorkerSelectStatus(
                principal.memberId(),
                workPlaceId,
                weekScheduleId
        );
    }

}
