package com.autoschedule.schedule.controller;

import com.autoschedule.auth.jwt.JwtAuthenticationPrincipal;
import com.autoschedule.global.security.annotation.OwnerOnly;
import com.autoschedule.schedule.dto.*;
import com.autoschedule.schedule.service.ScheduleGenerationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 자동 스케줄 생성, 미리보기 조회, 확정 API 요청을 처리한다.
 */
@Validated
@RestController
@RequiredArgsConstructor
public class ScheduleGenerationController {

    private final ScheduleGenerationService scheduleGenerationService;

    /**
     * 사장이 주간 스케줄 조건과 근무자 제출 조건을 기준으로 자동 스케줄 후보를 생성한다.
     */
    @OwnerOnly
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping("/api/work-places/{workPlaceId}/week-schedules/{weekScheduleId}/schedule-generation-runs")
    public ScheduleGenerationRunResponse generateSchedulePreview(
            @AuthenticationPrincipal JwtAuthenticationPrincipal principal,
            @PathVariable Long workPlaceId,
            @PathVariable Long weekScheduleId
    ) {
        return scheduleGenerationService.generateSchedulePreview(
                principal.memberId(),
                workPlaceId,
                weekScheduleId
        );
    }

    /**
     * 사장이 기존 자동 스케줄 생성 결과를 삭제 처리하고 새 미리보기를 다시 생성한다.
     */
    @OwnerOnly
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping("/api/work-places/{workPlaceId}/week-schedules/{weekScheduleId}/schedule-generation-runs/regenerate")
    public ScheduleGenerationRunResponse regenerateSchedulePreview(
            @AuthenticationPrincipal JwtAuthenticationPrincipal principal,
            @PathVariable Long workPlaceId,
            @PathVariable Long weekScheduleId
    ) {
        return scheduleGenerationService.regenerateSchedulePreview(
                principal.memberId(),
                workPlaceId,
                weekScheduleId
        );
    }

    /**
     * 사장이 자동 생성 실행 이력에 속한 미리보기 JSON을 조회한다.
     */
    @OwnerOnly
    @GetMapping("/api/work-places/{workPlaceId}/week-schedules/{weekScheduleId}/schedule-generation-runs/{runId}/preview")
    public SchedulePreviewResponse getSchedulePreview(
            @AuthenticationPrincipal JwtAuthenticationPrincipal principal,
            @PathVariable Long workPlaceId,
            @PathVariable Long weekScheduleId,
            @PathVariable Long runId
    ) {
        return scheduleGenerationService.getSchedulePreview(
                principal.memberId(),
                workPlaceId,
                weekScheduleId,
                runId
        );
    }

    /**
     * 사장이 자동 생성 미리보기 후보 중 하나를 확정 주간 스케줄로 전환한다.
     */
    @OwnerOnly
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping("/api/work-places/{workPlaceId}/week-schedules/{weekScheduleId}/confirmed-week-schedules")
    public ConfirmedWeekScheduleResponse confirmWeekSchedule(
            @AuthenticationPrincipal JwtAuthenticationPrincipal principal,
            @PathVariable Long workPlaceId,
            @PathVariable Long weekScheduleId,
            @Valid @RequestBody ConfirmWeekScheduleRequest request
    ) {
        return scheduleGenerationService.confirmWeekSchedule(
                principal.memberId(),
                workPlaceId,
                weekScheduleId,
                request
        );
    }

    /**
     * 사장이 확정된 주간 스케줄에 단건 근무 파트와 배정을 추가한다.
     */
    @OwnerOnly
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping("/api/work-places/{workPlaceId}/confirmed-week-schedules/{confirmedWeekScheduleId}/assignments")
    public ManualScheduleAssignmentResponse createManualAssignment(
            @AuthenticationPrincipal JwtAuthenticationPrincipal principal,
            @PathVariable Long workPlaceId,
            @PathVariable Long confirmedWeekScheduleId,
            @Valid @RequestBody ManualScheduleAssignmentCreateRequest request
    ) {
        return scheduleGenerationService.createManualAssignment(
                principal.memberId(),
                workPlaceId,
                confirmedWeekScheduleId,
                request
        );
    }

    /**
     * 사장이 확정된 주간 스케줄의 단건 근무 파트와 배정을 수정한다.
     */
    @OwnerOnly
    @PutMapping("/api/work-places/{workPlaceId}/confirmed-week-schedules/{confirmedWeekScheduleId}/time-details/{timeDetailId}/assignments")
    public ManualScheduleAssignmentResponse updateManualAssignment(
            @AuthenticationPrincipal JwtAuthenticationPrincipal principal,
            @PathVariable Long workPlaceId,
            @PathVariable Long confirmedWeekScheduleId,
            @PathVariable Long timeDetailId,
            @Valid @RequestBody ManualScheduleAssignmentRequest request
    ) {
        return scheduleGenerationService.updateManualAssignment(
                principal.memberId(),
                workPlaceId,
                confirmedWeekScheduleId,
                timeDetailId,
                request
        );
    }

    /**
     * 사장이 확정된 주간 스케줄의 단건 근무 파트와 배정을 삭제한다.
     */
    @OwnerOnly
    @DeleteMapping("/api/work-places/{workPlaceId}/confirmed-week-schedules/{confirmedWeekScheduleId}/time-details/{timeDetailId}/assignments")
    public ManualScheduleAssignmentDeleteResponse deleteManualAssignment(
            @AuthenticationPrincipal JwtAuthenticationPrincipal principal,
            @PathVariable Long workPlaceId,
            @PathVariable Long confirmedWeekScheduleId,
            @PathVariable Long timeDetailId
    ) {
        return scheduleGenerationService.deleteManualAssignment(
                principal.memberId(),
                workPlaceId,
                confirmedWeekScheduleId,
                timeDetailId
        );
    }
}
