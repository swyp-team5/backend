package com.autoschedule.schedulelogic.dto;

import java.util.List;

/**
 * 백트래킹 알고리즘으로 나온 경우의 수 하나를 나타낸다.
 */
public record ScheduleResultDto(
        Integer score,
        Double assignmentVariance, // 동일 점수 근무 배정 분산 정도로 정렬하기 위함
        List<ScheduleAssignmentDto> assignments
) {}