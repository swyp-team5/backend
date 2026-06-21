package com.autoschedule.schedulecondition.dto;

import com.autoschedule.schedulecondition.domain.WeekSchedule;
import com.autoschedule.schedulecondition.domain.WeekScheduleStatus;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 주간 스케줄 조건 생성 응답을 표현한다.
 */
public record WeekScheduleResponse(
        Long weekScheduleId,
        Long workPlaceId,
        String weekScheduleName,
        LocalDate dueDate,
        WeekScheduleStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    /**
     * 주간 스케줄 엔티티를 응답으로 변환한다.
     */
    public static WeekScheduleResponse from(WeekSchedule weekSchedule) {
        return new WeekScheduleResponse(
                weekSchedule.getId(),
                weekSchedule.getWorkPlace().getId(),
                weekSchedule.getWeekScheduleName(),
                weekSchedule.getDueDate(),
                weekSchedule.getStatus(),
                weekSchedule.getCreatedAt(),
                weekSchedule.getUpdatedAt()
        );
    }
}