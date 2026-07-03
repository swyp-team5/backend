package com.autoschedule.schedule.controller;

import com.autoschedule.auth.jwt.JwtAuthenticationPrincipal;
import com.autoschedule.global.security.annotation.OwnerOnly;
import com.autoschedule.global.security.annotation.WorkerOnly;
import com.autoschedule.schedule.dto.MyConfirmedScheduleResponse;
import com.autoschedule.schedule.dto.OwnerConfirmedScheduleResponse;
import com.autoschedule.schedule.dto.OwnerWeeklyConfirmedScheduleResponse;
import com.autoschedule.schedule.service.ConfirmedScheduleQueryService;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 확정 근무 스케줄 조회 API 요청을 처리한다.
 */
@RestController
@RequiredArgsConstructor
public class ConfirmedScheduleQueryController {

    private final ConfirmedScheduleQueryService confirmedScheduleQueryService;

    /**
     * 근무자 본인의 기간별 확정 근무 일정을 조회한다.
     */
    @WorkerOnly
    @GetMapping("/api/me/confirmed-schedules")
    public MyConfirmedScheduleResponse getMyConfirmedSchedules(
            @AuthenticationPrincipal JwtAuthenticationPrincipal principal,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return confirmedScheduleQueryService.getMyConfirmedSchedules(principal.memberId(), from, to);
    }

    /**
     * 사장이 소유한 사업장의 기간별 확정 근무표를 조회한다.
     */
    @OwnerOnly
    @GetMapping("/api/work-places/{workPlaceId}/confirmed-schedules")
    public OwnerConfirmedScheduleResponse getOwnerConfirmedSchedules(
            @AuthenticationPrincipal JwtAuthenticationPrincipal principal,
            @PathVariable Long workPlaceId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return confirmedScheduleQueryService.getOwnerConfirmedSchedules(
                principal.memberId(),
                workPlaceId,
                from,
                to
        );
    }

    /**
     * 사장이 소유한 사업장의 주간 확정 근무표를 조회한다.
     */
    @OwnerOnly
    @GetMapping("/api/work-places/{workPlaceId}/confirmed-schedules/weekly")
    public OwnerWeeklyConfirmedScheduleResponse getOwnerWeeklyConfirmedSchedules(
            @AuthenticationPrincipal JwtAuthenticationPrincipal principal,
            @PathVariable Long workPlaceId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStartDate
    ) {
        return confirmedScheduleQueryService.getOwnerWeeklyConfirmedSchedules(
                principal.memberId(),
                workPlaceId,
                weekStartDate
        );
    }
}
