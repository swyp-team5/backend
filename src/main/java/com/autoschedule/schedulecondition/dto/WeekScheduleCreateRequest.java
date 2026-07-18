package com.autoschedule.schedulecondition.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * 사장이 선택한 스케줄 조건 생성 요청 값을 검증한다.
 */
public record WeekScheduleCreateRequest(

        @NotNull(message = "가게 오픈 시간은 필수입니다.")
        LocalTime workPlaceOpenTime,

        @NotNull(message = "가게 마감 시간은 필수입니다.")
        LocalTime workPlaceCloseTime,

        @NotNull(message = "최소 근무 횟수는 필수입니다.")
        @Min(value = 1, message = "최소 근무 횟수는 1 이상이어야 합니다.")
        Integer minPersonalWorkCount,

        @NotNull(message = "최대 근무 횟수는 필수입니다.")
        @Min(value = 1, message = "최대 근무 횟수는 1 이상이어야 합니다.")
        Integer maxPersonalWorkCount,

        @NotNull(message = "제출 마감일은 필수입니다.")
        LocalDate dueDate,

        @NotEmpty(message = "요일 선택은 필수입니다.")
        List<@Valid DayCreateRequest> days
) {
}
