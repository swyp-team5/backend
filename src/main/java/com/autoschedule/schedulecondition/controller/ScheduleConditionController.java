package com.autoschedule.schedulecondition.controller;

import com.autoschedule.auth.jwt.JwtAuthenticationPrincipal;
import com.autoschedule.global.security.annotation.OwnerOnly;
import com.autoschedule.global.security.annotation.WorkerOnly;
import com.autoschedule.schedulecondition.dto.*;
import com.autoschedule.schedulecondition.service.ScheduleConditionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

/**
 * 스케줄 조건 API 요청을 처리한다.
 */
@Validated
@RestController
@RequiredArgsConstructor
public class ScheduleConditionController {

    private final ScheduleConditionService scheduleConditionService;

    /**
     * 사장이 선택한 스케줄 조건을 저장한다.
     */
    @OwnerOnly
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping("/api/work-places/{workPlaceId}/schedule-conditions")
    public WeekScheduleResponse createScheduleCondition(
            @AuthenticationPrincipal JwtAuthenticationPrincipal principal,
            @PathVariable Long workPlaceId,
            @Valid @RequestBody WeekScheduleCreateRequest request
    ) {
        return scheduleConditionService.createScheduleCondition(
                principal.memberId(),
                workPlaceId,
                request
        );
    }

    /**
     * 사장이 가장 최근 생성한 스케줄 조건을 조회한다.
     */
    @OwnerOnly
    @GetMapping("/api/work-places/{workPlaceId}/schedule-conditions/latest")
    public WeekScheduleLatestResponse getLatestScheduleCondition(
            @AuthenticationPrincipal JwtAuthenticationPrincipal principal,
            @PathVariable Long workPlaceId
    ) {
        return scheduleConditionService.getLatestScheduleCondition(
                principal.memberId(),
                workPlaceId
        );
    }

    /**
     * 사장이 생성한 스케줄 조건을 초기화한다.
     */
    @OwnerOnly
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @DeleteMapping("/api/work-places/{workPlaceId}/schedule-conditions/{weekScheduleId}")
    public void deleteScheduleCondition(
            @AuthenticationPrincipal JwtAuthenticationPrincipal principal,
            @PathVariable Long workPlaceId,
            @PathVariable Long weekScheduleId
    ) {
        scheduleConditionService.deleteScheduleCondition(
                principal.memberId(),
                workPlaceId,
                weekScheduleId
        );
    }

    /**
     * 달력 활성화용 날짜 목록을 조회한다. 근무자들이 근무 불가능한 일자를 선택할 때 보는 달력이다.
     */
    @WorkerOnly
    @GetMapping("/api/work-places/{workPlaceId}/schedule-conditions/calendar-activate")
    public ScheduleConditionCalendarResponse getCalendarActivateDates(
            @AuthenticationPrincipal JwtAuthenticationPrincipal principal,
            @PathVariable Long workPlaceId
    ) {
        return scheduleConditionService.getCalendarActivateDates(
                principal.memberId(),
                workPlaceId
        );
    }

    /**
     * 특정 일자의 타임 상세 정보를 조회한다.
     */
    @WorkerOnly
    @GetMapping("/api/work-places/{workPlaceId}/week-schedules/{weekScheduleId}/days/{date}/time-details")
    public DayTimeDetailResponse getDayTimeDetails(
            @AuthenticationPrincipal JwtAuthenticationPrincipal principal,
            @PathVariable Long workPlaceId,
            @PathVariable Long weekScheduleId,
            @PathVariable LocalDate date
    ) {
        return scheduleConditionService.getDayTimeDetails(
                principal.memberId(),
                workPlaceId,
                weekScheduleId,
                date
        );
    }

}
