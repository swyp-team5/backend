package com.autoschedule.schedulecondition.service;

import com.autoschedule.global.exception.ApiException;
import com.autoschedule.global.exception.ErrorCode;
import com.autoschedule.member.domain.Member;
import com.autoschedule.member.repository.MemberRepository;
import com.autoschedule.schedulecondition.domain.*;
import com.autoschedule.schedulecondition.dto.*;
import com.autoschedule.schedulecondition.repository.DayRepository;
import com.autoschedule.schedulecondition.repository.TimeDetailRepository;
import com.autoschedule.schedulecondition.repository.WeekScheduleRepository;
import com.autoschedule.workplace.domain.WorkPlace;
import com.autoschedule.workplace.repository.WorkPlaceRepository;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.time.temporal.WeekFields;
import java.util.*;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 스케줄 조건 생성, 조회, 상세조회 비즈니스 규칙을 처리한다.
 */
@Service
@RequiredArgsConstructor
public class ScheduleConditionService {

    private final MemberRepository memberRepository;
    private final WorkPlaceRepository workPlaceRepository;
    private final WeekScheduleRepository weekScheduleRepository;
    private final DayRepository dayRepository;
    private final TimeDetailRepository timeDetailRepository;

    /**
     * 사장이 선택한 스케줄 조건을 저장한다.
     */
    @Transactional
    public WeekScheduleResponse createScheduleCondition(
            Long ownerMemberId,
            Long workPlaceId,
            WeekScheduleCreateRequest request
    ) {
        Member owner = findActiveMember(ownerMemberId); // 현재 로그인한 사장 회원을 조회함
        WorkPlace workPlace = findOwnedActiveWorkPlace(workPlaceId, owner.getId()); // 사장회원이 이 사업장의 사장인지 확인

        validateScheduleCondition(request); // 요청값들에 대해서 검증 코드

        LocalDate today = LocalDate.now(); // 오늘 일자를 기준으로 다음주 일자를 가져오는 코드

        validateNextWeekScheduleNotDuplicated(workPlace.getId(), today);

        // creat() -> 엔티티를 만듬 / save() -> DB에 자징함
        WeekSchedule weekSchedule = weekScheduleRepository.save(
                WeekSchedule.create(
                        workPlace, // 사업장 id
                        createNextWeekScheduleName(today), // 오늘 일자를 기준으로 다음주 일자에 대한 '0월 0주차' 형태로 변형해서 저장시킴
                        today.plusDays(3), // 오늘 일자에서 3일을 더한 일자를 마감일로 정한다
                        request.workPlaceOpenTime(),
                        request.workPlaceCloseTime(),
                        request.minPersonalWorkCount(),
                        request.maxPersonalWorkCount()
                )
        );

        // 요일별 저장되는 정보를 for문을 통해서 저장시킴
        for (DayCreateRequest dayRequest : request.days()) {
            Day day = dayRepository.save(
                    Day.create(
                            weekSchedule,
                            dayRequest.dayName(),
                            dayRequest.date(),
                            dayRequest.groupingId(),
                            dayRequest.workChangeCount(),
                            dayRequest.holidayStatus(),
                            dayRequest.selectLimitStatus()
                    )
            );

            // 요일별 시간별 근무 상세조건 정보를 for문을 통해 저장시킴
            for (TimeDetailCreateRequest timeDetailRequest : dayRequest.timeDetails()) {
                timeDetailRepository.save(
                        TimeDetail.create(
                                day,
                                timeDetailRequest.workPartNo(),
                                timeDetailRequest.timeName(),
                                timeDetailRequest.workerCount(),
                                timeDetailRequest.startTime(),
                                timeDetailRequest.closeTime(),
                                timeDetailRequest.restTime()
                        )
                );
            }
        }

        return WeekScheduleResponse.from(weekSchedule); // 값들을 저장시킨뒤 주간스케줄 정보를 응답값으로 전달함
    }

    /**
     * 사장이 가장 최근 생성한 스케줄 조건을 조회한다. (불러오기 기능)
     */
    @Transactional(readOnly = true)
    public WeekScheduleLatestResponse getLatestScheduleCondition(
            Long ownerMemberId,
            Long workPlaceId
    ) {
        Member owner = findActiveMember(ownerMemberId);

        WorkPlace workPlace = findOwnedActiveWorkPlace(
                workPlaceId,
                owner.getId()
        );

        WeekSchedule weekSchedule = weekScheduleRepository
                .findFirstByWorkPlace_IdAndStatusAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(
                        workPlace.getId(),
                        WeekScheduleStatus.ACTIVE
                )
                .orElseThrow(() -> new ApiException(
                        ErrorCode.RESOURCE_NOT_FOUND,
                        "최근 스케줄 조건을 찾을 수 없습니다."
                ));

        List<Day> days = dayRepository
                .findByWeekSchedule_IdAndStatusAndDeletedAtIsNullOrderByDateAscIdAsc(
                        weekSchedule.getId(),
                        DayStatus.ACTIVE
                );

        List<ScheduleConditionGroupResponse> groups = createGroupResponses(days);

        return WeekScheduleLatestResponse.from(weekSchedule, groups);
    }

    /**
     * 근무자가 달력에서 선택 가능한 날짜 상태를 확인할 수 있도록
     * 가장 최근 스케줄 조건의 일~토 7일 정보를 조회한다.
     */
    @Transactional(readOnly = true)
    public ScheduleConditionCalendarResponse getCalendarActivateDates(
            Long memberId,
            Long workPlaceId
    ) {
        findActiveMember(memberId);

        WeekSchedule weekSchedule = weekScheduleRepository
                .findFirstByWorkPlace_IdAndStatusAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(
                        workPlaceId,
                        WeekScheduleStatus.ACTIVE
                )
                .orElseThrow(() -> new ApiException(
                        ErrorCode.RESOURCE_NOT_FOUND,
                        "진행 중인 스케줄 조건을 찾을 수 없습니다."
                ));

        List<Day> days = dayRepository
                .findByWeekSchedule_IdAndStatusAndDeletedAtIsNullOrderByDateAscIdAsc(
                        weekSchedule.getId(),
                        DayStatus.ACTIVE
                );

        validateSevenDays(days);

        List<AvailableDateResponse> availableDates = days.stream()
                .map(AvailableDateResponse::from)
                .toList();

        return ScheduleConditionCalendarResponse.from(
                weekSchedule,
                availableDates
        );
    }

    /**
     * 특정 일자의 타임 상세 정보를 조회한다.
     */
    @Transactional(readOnly = true)
    public DayTimeDetailResponse getDayTimeDetails(
            Long memberId,
            Long weekScheduleId,
            LocalDate date
    ) {
        findActiveMember(memberId);

        Day day = dayRepository
                .findByWeekSchedule_IdAndDateAndStatusAndDeletedAtIsNull(
                        weekScheduleId,
                        date,
                        DayStatus.ACTIVE
                )
                .orElseThrow(() -> new ApiException(
                        ErrorCode.RESOURCE_NOT_FOUND,
                        "해당 일자의 스케줄 조건을 찾을 수 없습니다."
                ));

        List<TimeDetailSummaryResponse> timeDetails = timeDetailRepository
                .findByDay_IdAndStatusAndDeletedAtIsNullOrderByWorkPartNoAsc(
                        day.getId(),
                        TimeDetailStatus.ACTIVE
                )
                .stream()
                .map(TimeDetailSummaryResponse::from)
                .toList();

        return DayTimeDetailResponse.from(day, timeDetails);
    }

    /**
     * 달력 활성화 응답에 필요한 일~토 7일 정보가 모두 존재하는지 검증한다.
     */
    private void validateSevenDays(List<Day> days) {
        if (days.size() != 7) {
            throw new ApiException(
                    ErrorCode.INVALID_REQUEST,
                    "스케줄 조건의 요일 정보가 올바르지 않습니다."
            );
        }
    }

    /**
     * 요일 조건 목록을 groupingId 기준으로 묶어 응답으로 변환한다.
     */
    private List<ScheduleConditionGroupResponse> createGroupResponses(
            List<Day> days
    ) {
        return days.stream()
                .filter(day -> day.getGroupingId() != null)
                .collect(Collectors.groupingBy(Day::getGroupingId))
                .entrySet()
                .stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> createGroupResponse(
                        entry.getKey(),
                        entry.getValue()
                ))
                .toList();
    }

    /**
     * 하나의 groupingId에 속한 요일 조건을 그룹 응답으로 변환한다.
     */
    private ScheduleConditionGroupResponse createGroupResponse(
            Integer groupingId,
            List<Day> groupedDays
    ) {
        Day representativeDay = groupedDays.stream()
                .min(Comparator.comparing(Day::getDate))
                .orElseThrow();

        WeekSchedule weekSchedule = representativeDay.getWeekSchedule();

        List<TimeDetail> timeDetails = timeDetailRepository
                .findByDay_IdAndStatusAndDeletedAtIsNullOrderByWorkPartNoAsc(
                        representativeDay.getId(),
                        TimeDetailStatus.ACTIVE
                );

        return ScheduleConditionGroupResponse.from(
                groupingId,
                weekSchedule,
                groupedDays,
                timeDetails
        );
    }

    /**
     *  스케줄 조건 검증 메서드
     */
    private void validateScheduleCondition(WeekScheduleCreateRequest request) {
        Set<String> dayKeys = new HashSet<>();

        for (DayCreateRequest dayRequest : request.days()) {
            validateDayCondition(dayRequest, request);

            String dayKey = dayRequest.groupingId() + ":" + dayRequest.dayName();

            if (!dayKeys.add(dayKey)) {
                throw new ApiException(
                        ErrorCode.VALIDATION_FAILED,
                        "같은 그룹 안에 동일한 요일이 중복될 수 없습니다."
                );
            }
        }
    }

    /**
     *  일자별 조건 검증 메서드
     */
    private void validateDayCondition(DayCreateRequest dayRequest, WeekScheduleCreateRequest weekRequest) {

        if(dayRequest.groupingId()==null){
            return;
        } else {
            int expectedTimeDetailCount = dayRequest.workChangeCount() + 1;

            if (dayRequest.timeDetails().size() != expectedTimeDetailCount) {
                throw new ApiException(
                        ErrorCode.VALIDATION_FAILED,
                        "근무 교대 횟수와 타임별 상세 정보 개수가 일치하지 않습니다."
                );
            }

            if (!weekRequest.workPlaceOpenTime().isBefore(weekRequest.workPlaceCloseTime())) {
                throw new ApiException(
                        ErrorCode.VALIDATION_FAILED,
                        "가게 오픈 시간은 마감 시간보다 빨라야 합니다."
                );
            }

            if (weekRequest.minPersonalWorkCount() > weekRequest.maxPersonalWorkCount()) {
                throw new ApiException(
                        ErrorCode.VALIDATION_FAILED,
                        "최소 근무 횟수는 최대 근무 횟수보다 클 수 없습니다."
                );
            }

            validateTimeDetails(dayRequest, weekRequest);
        }
    }

    /**
     *  일자별 시간별 근무 상세조건 검증 메서드
     */
    private void validateTimeDetails(DayCreateRequest dayRequest, WeekScheduleCreateRequest weekRequest) {
        Set<Long> workPartNumbers = new HashSet<>();

        for (TimeDetailCreateRequest timeDetailRequest : dayRequest.timeDetails()) {
            if (!workPartNumbers.add(timeDetailRequest.workPartNo())) {
                throw new ApiException(
                        ErrorCode.VALIDATION_FAILED,
                        "같은 요일 안에서 근무 파트 번호는 중복될 수 없습니다."
                );
            }

            if (!timeDetailRequest.startTime().isBefore(timeDetailRequest.closeTime())) {
                throw new ApiException(
                        ErrorCode.VALIDATION_FAILED,
                        "근무 시작 시간은 종료 시간보다 빨라야 합니다."
                );
            }

            if (timeDetailRequest.startTime().isBefore(weekRequest.workPlaceOpenTime())
                    || timeDetailRequest.closeTime().isAfter(weekRequest.workPlaceCloseTime())) {
                throw new ApiException(
                        ErrorCode.VALIDATION_FAILED,
                        "근무 시간은 가게 운영 시간 안에 있어야 합니다."
                );
            }
        }
    }
    /**
     *  오늘 기준 다음 주의 스케줄 조건이 존재하는지 확인하는 메서드
     */
    private void validateNextWeekScheduleNotDuplicated(Long workPlaceId, LocalDate today) {
        String nextWeekScheduleName = createNextWeekScheduleName(today);
        boolean exists = weekScheduleRepository
                .existsByWorkPlace_IdAndWeekScheduleNameAndStatusAndDeletedAtIsNull(
                        workPlaceId,
                        nextWeekScheduleName,
                        WeekScheduleStatus.ACTIVE
                );
        if (exists) {
            throw new ApiException(
                    ErrorCode.CONFLICT,
                    "다음 주차 스케줄 조건이 이미 존재합니다."
            );
        }
    }

    /**
     *  오늘 기준 다음 주의 "0월 0주차" 이름을 생성시키는 메서드
     */
    private String createNextWeekScheduleName(LocalDate date) {

        LocalDate nextMonday = date.with(TemporalAdjusters.next(DayOfWeek.MONDAY));

        int month = nextMonday.getMonthValue();
        int weekOfMonth = nextMonday.get(WeekFields.of(Locale.KOREA).weekOfMonth());

        return month + "월 " + weekOfMonth + "주차";
    }

    /**
     *  활성화된 멤버인지 검증하는 메서드
     */
    private Member findActiveMember(Long memberId) {
        return memberRepository.findActiveById(memberId)
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED, "인증 정보가 올바르지 않습니다."));
    }

    /**
     *  사장이 해당 사업장에 맞는 사장인지 검증하는 메서드
     */
    private WorkPlace findOwnedActiveWorkPlace(Long workPlaceId, Long ownerMemberId) {
        return workPlaceRepository.findOwnedActiveById(workPlaceId, ownerMemberId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "조회할 수 있는 사업장을 찾을 수 없습니다."));
    }
}