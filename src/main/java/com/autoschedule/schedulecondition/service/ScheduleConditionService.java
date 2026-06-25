package com.autoschedule.schedulecondition.service;

import com.autoschedule.crew.domain.CrewStatus;
import com.autoschedule.crew.repository.CrewRepository;
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
    private final CrewRepository crewRepository;

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

        LocalDate today = LocalDate.now(); // 오늘 일자를 가져오는 코드

        // 다음 주 시작일 전날(일요일)을 최대 마감일로 제한
        LocalDate nextMonday = today.with(TemporalAdjusters.next(DayOfWeek.MONDAY));
        LocalDate maxDueDate = nextMonday.minusDays(1);
        LocalDate dueDate = today.plusDays(3).isAfter(maxDueDate) ? maxDueDate : today.plusDays(3);

        validateNextWeekScheduleNotDuplicated(workPlace.getId(), today);

        // creat() -> 엔티티를 만듬 / save() -> DB에 자징함
        WeekSchedule weekSchedule = weekScheduleRepository.save(
                WeekSchedule.create(
                        workPlace, // 사업장 id
                        createNextWeekScheduleName(today), // 오늘 일자를 기준으로 다음주 일자에 대한 '0월 0주차' 형태로 변형해서 저장시킴
                        dueDate,   // today.plusDays(3) 대신 계산된 dueDate 사용
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

        validateCrewMember(
                workPlaceId,
                memberId
        );

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
            Long workPlaceId,
            Long weekScheduleId,
            LocalDate date
    ) {
        findActiveMember(memberId);

        validateCrewMember(
                workPlaceId,
                memberId
        );

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
    private List<ScheduleConditionGroupResponse> createGroupResponses(List<Day> days) {
        Map<Integer, List<Day>> groupedDays = days.stream()
                .filter(day -> day.getGroupingId() != null)
                .collect(Collectors.groupingBy(Day::getGroupingId));

        // 각 그룹의 대표 Day (날짜 가장 빠른 것) ID 목록
        List<Long> representativeDayIds = groupedDays.values().stream()
                .map(groupDayList -> groupDayList.stream()
                        .min(Comparator.comparing(Day::getDate))
                        .orElseThrow())
                .map(Day::getId)
                .toList();

        // IN 쿼리 1번으로 전체 조회 후 dayId별로 그룹핑
        Map<Long, List<TimeDetail>> timeDetailsByDayId = timeDetailRepository
                .findByDay_IdInAndStatusAndDeletedAtIsNullOrderByWorkPartNoAsc(
                        representativeDayIds,
                        TimeDetailStatus.ACTIVE
                )
                .stream()
                .collect(Collectors.groupingBy(td -> td.getDay().getId()));

        return groupedDays.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> createGroupResponse(entry.getKey(), entry.getValue(), timeDetailsByDayId))
                .toList();
    }

    /**
     * 하나의 groupingId에 속한 요일 조건을 그룹 응답으로 변환한다.
     */
    private ScheduleConditionGroupResponse createGroupResponse(
            Integer groupingId,
            List<Day> groupedDays,
            Map<Long, List<TimeDetail>> timeDetailsByDayId
    ) {
        Day representativeDay = groupedDays.stream()
                .min(Comparator.comparing(Day::getDate))
                .orElseThrow();

        WeekSchedule weekSchedule = representativeDay.getWeekSchedule();

        List<TimeDetail> timeDetails = timeDetailsByDayId.getOrDefault(
                representativeDay.getId(),
                List.of()
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

        // 1. 정확히 7일인지 검증
        if (request.days().size() != 7) {
            throw new ApiException(
                    ErrorCode.VALIDATION_FAILED,
                    "스케줄 조건은 7일(월~일)을 모두 포함해야 합니다."
            );
        }

        LocalDate nextMonday = LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.MONDAY));
        LocalDate nextSunday = nextMonday.plusDays(6);

        Set<String> dayKeys = new HashSet<>();
        Set<LocalDate> dateKeys = new HashSet<>();

        for (DayCreateRequest dayRequest : request.days()) {
            validateDayCondition(dayRequest, request);

            LocalDate date = dayRequest.date();

            // 2. 날짜가 다음 주(월~일) 범위 내에 있는지 검증
            if (date.isBefore(nextMonday) || date.isAfter(nextSunday)) {
                throw new ApiException(
                        ErrorCode.VALIDATION_FAILED,
                        "날짜는 다음 주(" + nextMonday + " ~ " + nextSunday + ") 범위 내에 있어야 합니다."
                );
            }

            // 3. dayName과 실제 날짜의 요일이 일치하는지 검증
            ScheduleDayName actualDayName = ScheduleDayName.valueOf(date.getDayOfWeek().name());
            if (dayRequest.dayName() != actualDayName) {
                throw new ApiException(
                        ErrorCode.VALIDATION_FAILED,
                        "dayName과 실제 날짜의 요일이 일치하지 않습니다."
                );
            }

            // 4. 날짜 중복 검증
            if (!dateKeys.add(date)) {
                throw new ApiException(
                        ErrorCode.VALIDATION_FAILED,
                        "동일한 날짜가 중복될 수 없습니다."
                );
            }

            // 기존: 같은 그룹 내 동일 요일 중복 검증
            String dayKey = dayRequest.groupingId() + ":" + dayRequest.dayName();
            if (!dayKeys.add(dayKey)) {
                throw new ApiException(
                        ErrorCode.VALIDATION_FAILED,
                        "같은 그룹 안에 동일한 요일이 중복될 수 없습니다."
                );
            }
        }
        validateSameGroupConditions(request.days());
    }

    /**
     *  스케줄 조건 같은 그룹내 일자들 이모두 같은 값을 갖는지 검증 메서드
     */
    private void validateSameGroupConditions(List<DayCreateRequest> days) {
        Map<Integer, List<DayCreateRequest>> groupedDays = days.stream()
                .filter(d -> d.groupingId() != null)
                .collect(Collectors.groupingBy(DayCreateRequest::groupingId));

        for (List<DayCreateRequest> groupDays : groupedDays.values()) {
            DayCreateRequest first = groupDays.get(0);

            List<TimeDetailCreateRequest> firstDetails = first.timeDetails().stream()
                    .sorted(Comparator.comparingInt(TimeDetailCreateRequest::workPartNo))
                    .toList();

            for (DayCreateRequest other : groupDays) {
                if (!Objects.equals(first.workChangeCount(), other.workChangeCount())) {
                    throw new ApiException(
                            ErrorCode.VALIDATION_FAILED,
                            "같은 그룹 내 모든 요일의 workChangeCount가 동일해야 합니다."
                    );
                }

                List<TimeDetailCreateRequest> otherDetails = other.timeDetails().stream()
                        .sorted(Comparator.comparingInt(TimeDetailCreateRequest::workPartNo))
                        .toList();

                if (firstDetails.size() != otherDetails.size()) {
                    throw new ApiException(
                            ErrorCode.VALIDATION_FAILED,
                            "같은 그룹 내 모든 요일의 타임 개수가 동일해야 합니다."
                    );
                }

                for (int i = 0; i < firstDetails.size(); i++) {
                    TimeDetailCreateRequest fd = firstDetails.get(i);
                    TimeDetailCreateRequest od = otherDetails.get(i);
                    if (!Objects.equals(fd.workPartNo(), od.workPartNo())
                            || !Objects.equals(fd.startTime(), od.startTime())
                            || !Objects.equals(fd.closeTime(), od.closeTime())
                            || !Objects.equals(fd.workerCount(), od.workerCount())
                            || !Objects.equals(fd.restTime(), od.restTime())) {
                        throw new ApiException(
                                ErrorCode.VALIDATION_FAILED,
                                "같은 그룹 내 모든 요일의 타임 상세 조건(파트번호, 시작/종료 시간, 필요 인원, 휴게 시간)이 동일해야 합니다."
                        );
                    }
                }
            }
        }
    }

    /**
     *  일자별 조건 검증 메서드
     */
    private void validateDayCondition(DayCreateRequest dayRequest, WeekScheduleCreateRequest weekRequest) {

        // weekRequest 수준 검증 — groupingId 여부와 무관하게 항상 실행
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

        if (dayRequest.groupingId() == null) {
            // 휴일: 타임 상세 정보가 없어야 함
            if (dayRequest.timeDetails() != null && !dayRequest.timeDetails().isEmpty()) {
                throw new ApiException(
                        ErrorCode.VALIDATION_FAILED,
                        "그룹이 없는 날(휴일)에는 타임 상세 정보를 입력할 수 없습니다."
                );
            }
            return;
        }

        int expectedTimeDetailCount = dayRequest.workChangeCount() + 1;
        if (dayRequest.timeDetails().size() != expectedTimeDetailCount) {
            throw new ApiException(
                    ErrorCode.VALIDATION_FAILED,
                    "근무 교대 횟수와 타임별 상세 정보 개수가 일치하지 않습니다."
            );
        }

        validateTimeDetails(dayRequest, weekRequest);
    }

    /**
     *  일자별 시간별 근무 상세조건 검증 메서드
     */
    private void validateTimeDetails(DayCreateRequest dayRequest, WeekScheduleCreateRequest weekRequest) {
        Set<Integer> workPartNumbers = new HashSet<>();

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

        // 교대 시간 겹침 검증
        List<TimeDetailCreateRequest> sorted = dayRequest.timeDetails().stream()
                .sorted(Comparator.comparing(TimeDetailCreateRequest::startTime))
                .toList();

        for (int i = 0; i < sorted.size() - 1; i++) {
            TimeDetailCreateRequest current = sorted.get(i);
            TimeDetailCreateRequest next = sorted.get(i + 1);

            if (!current.closeTime().isBefore(next.startTime())) {
                throw new ApiException(
                        ErrorCode.VALIDATION_FAILED,
                        "교대 시간이 겹칩니다. (" + current.startTime() + "~" + current.closeTime()
                                + " / " + next.startTime() + "~" + next.closeTime() + ")"
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
     * 로그인 회원이 해당 사업장의 크루원인지 확인한다.
     */
    private void validateCrewMember(
            Long workPlaceId,
            Long memberId
    ) {
        boolean exists = crewRepository.existsByMember_IdAndWorkPlace_IdAndStatus(
                memberId,
                workPlaceId,
                CrewStatus.ACTIVE
        );

        if (!exists) {
            throw new ApiException(ErrorCode.FORBIDDEN, "이 회원은 해당 사업장의 크루원이 아닙니다.");
        }
    }

    /**
     *  사장이 해당 사업장에 맞는 사장인지 검증하는 메서드
     */
    private WorkPlace findOwnedActiveWorkPlace(Long workPlaceId, Long ownerMemberId) {
        return workPlaceRepository.findOwnedActiveById(workPlaceId, ownerMemberId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "조회할 수 있는 사업장을 찾을 수 없습니다."));
    }
}