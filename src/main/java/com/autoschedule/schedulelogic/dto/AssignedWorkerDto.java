package com.autoschedule.schedulelogic.dto;

/**
 * 배정된 근무자 정보를 나타낸다.
 */
public record AssignedWorkerDto(
        Long memberId,
        String memberName
) {}