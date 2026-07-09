package com.autoschedule.schedule.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * 확정 스케줄의 단건 근무 파트 추가 요청 값을 검증한다.
 * workPartNo는 서버가 해당 날짜의 마지막 파트 번호 기준으로 자동 부여한다.
 */
public record ManualScheduleAssignmentCreateRequest(

    @NotNull(message = "근무 날짜는 필수입니다.")
    LocalDate workDate,

    @Size(max = 20, message = "타임 이름은 20자 이하로 입력해야 합니다.")
    String timeName,

    @NotNull(message = "근무 시작 시간은 필수입니다.")
    LocalTime startTime,

    @NotNull(message = "근무 종료 시간은 필수입니다.")
    LocalTime closeTime,

    @NotNull(message = "휴게 시간은 필수입니다.")
    @PositiveOrZero(message = "휴게 시간은 0분 이상이어야 합니다.")
    Integer restTime,

    @NotEmpty(message = "배정할 근무자는 1명 이상이어야 합니다.")
    List<@NotNull(message = "근무자 ID는 필수입니다.") Long> workerMemberIds
) {
}
