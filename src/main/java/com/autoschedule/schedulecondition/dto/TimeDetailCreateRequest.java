package com.autoschedule.schedulecondition.dto;

import jakarta.validation.constraints.*;
import java.time.LocalTime;

/**
 * 타임별 상세 정보 요청 값을 검증한다.
 */
public record TimeDetailCreateRequest(

        @NotNull(message = "근무 파트 번호는 필수입니다.")
        @Min(value = 1, message = "근무 파트 번호는 1 이상이어야 합니다.")
        Integer workPartNo,

        @Size(max = 20, message = "타임 이름은 20자 이하여야 합니다.")
        String timeName,

        @NotNull(message = "필요 근무자 수는 필수입니다.")
        @Min(value = 1, message = "필요 근무자 수는 1 이상이어야 합니다.")
        Integer workerCount,

        @NotNull(message = "근무 시작 시간은 필수입니다.")
        LocalTime startTime,

        @NotNull(message = "근무 종료 시간은 필수입니다.")
        LocalTime closeTime,

        @NotNull(message = "휴게 시간은 필수입니다.")
        @Min(value = 0, message = "휴게 시간은 0분 이상이어야 합니다.")
        Integer restTime
) {
}