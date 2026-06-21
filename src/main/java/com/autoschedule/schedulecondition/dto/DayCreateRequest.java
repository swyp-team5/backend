package com.autoschedule.schedulecondition.dto;

import com.autoschedule.schedulecondition.domain.ScheduleDayName;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * 요일별 스케줄 조건 요청 값을 검증한다.
 */
public record DayCreateRequest(

        @NotNull(message = "요일 이름은 필수입니다.")
        ScheduleDayName dayName,

        @NotNull(message = "일자는 필수입니다.")
        LocalDate date,

        @Min(value = 1, message = "그룹핑 ID는 1 이상이어야 합니다.")
        Integer groupingId,

        @Min(value = 0, message = "근무 교대 횟수는 0 이상이어야 합니다.")
        Integer workChangeCount,

        @NotNull(message = "휴일 여부는 필수입니다.")
        Boolean holidayStatus,

        @NotNull(message = "선택 제한 여부는 필수입니다.")
        Boolean selectLimitStatus,

        List<@Valid TimeDetailCreateRequest> timeDetails
) {
}