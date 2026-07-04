package com.autoschedule.schedulelogic.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * time_detail 하나에 대한 배정 결과를 나타낸다.
 */
public record ScheduleAssignmentDto(
        Long timeDetailId,
        String dayName,
        LocalDate date,
        String timeName,
        Integer requiredWorkerCount,
        Integer assignedWorkerCount,
        Boolean isFulfilled,
        List<AssignedWorkerDto> assignedWorkers
) {}