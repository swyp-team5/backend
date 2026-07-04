package com.autoschedule.schedulelogic.service;

import com.autoschedule.global.exception.ApiException;
import com.autoschedule.global.exception.ErrorCode;
import com.autoschedule.member.domain.Member;
import com.autoschedule.member.repository.MemberRepository;
import com.autoschedule.schedulecondition.domain.*;
import com.autoschedule.schedulecondition.repository.DayRepository;
import com.autoschedule.schedulecondition.repository.TimeDetailRepository;
import com.autoschedule.schedulecondition.repository.WeekScheduleRepository;
import com.autoschedule.schedulelogic.dto.*;
import com.autoschedule.workerselect.domain.WorkerSelectSubmission;
import com.autoschedule.workerselect.domain.WorkerSelectSubmissionStatus;
import com.autoschedule.workerselect.domain.WorkerUnavailableTimeDetail;
import com.autoschedule.workerselect.repository.WorkerSelectSubmissionRepository;
import com.autoschedule.workerselect.repository.WorkerUnavailableTimeDetailRepository;
import com.autoschedule.workplace.domain.WorkPlace;
import com.autoschedule.workplace.domain.WorkPlaceStatus;
import com.autoschedule.workplace.repository.WorkPlaceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 자동 스케줄링 데이터 수집 및 알고리즘 호출을 담당한다.
 */
@Service
@RequiredArgsConstructor
public class ScheduleLogicService {

    private final MemberRepository memberRepository;
    private final WorkPlaceRepository workPlaceRepository;
    private final WeekScheduleRepository weekScheduleRepository;
    private final DayRepository dayRepository;
    private final TimeDetailRepository timeDetailRepository;
    private final WorkerSelectSubmissionRepository workerSelectSubmissionRepository;
    private final WorkerUnavailableTimeDetailRepository workerUnavailableTimeDetailRepository;
    private final ScheduleAlgorithmService scheduleAlgorithmService;

    @Transactional(readOnly = true)
    public ScheduleLogicResponse generateSchedule(
            Long ownerMemberId,
            Long workPlaceId,
            Long weekScheduleId
    ) {
        // 1. 사장 및 사업장 검증
        Member owner = findActiveMember(ownerMemberId);
        WorkPlace workPlace = findOwnedActiveWorkPlace(workPlaceId, owner.getId());
        WeekSchedule weekSchedule = findActiveWeekSchedule(weekScheduleId, workPlace.getId());

        // 2. 요일 목록 조회 (날짜 오름차순)
        List<Day> days = dayRepository
                .findByWeekSchedule_IdAndStatusAndDeletedAtIsNullOrderByDateAscIdAsc(weekSchedule.getId(), DayStatus.ACTIVE);

        // 3. 스케줄링 대상 time_detail 조회
        List<Long> dayIds = days.stream().map(Day::getId).toList();
        List<TimeDetail> allTimeDetails = timeDetailRepository
                .findByDay_IdInAndStatusAndDeletedAtIsNullOrderByWorkPartNoAsc(dayIds, TimeDetailStatus.ACTIVE);

        // 4. 불가 일정 제출한 근무자 목록 조회
        List<WorkerSelectSubmission> submissions = workerSelectSubmissionRepository
                .findByWorkPlaceIdAndWeekScheduleIdAndStatusAndDeletedAtIsNull(
                        workPlace.getId(),
                        weekSchedule.getId(),
                        WorkerSelectSubmissionStatus.ACTIVE
                );

        // 5. 근무자별 불가 time_detail_id 목록 Map으로 구성
        // key: memberId, value: 불가 timeDetailId Set
        Map<Long, Set<Long>> unavailableMap = new HashMap<>();
        for (WorkerSelectSubmission submission : submissions) {
            List<WorkerUnavailableTimeDetail> unavailableDetails =
                    workerUnavailableTimeDetailRepository.findBySubmission_Id(submission.getId());
            Set<Long> unavailableIds = unavailableDetails.stream()
                    .map(d -> d.getTimeDetail().getId())
                    .collect(Collectors.toSet());
            unavailableMap.put(submission.getMemberId(), unavailableIds);
        }

        // 6. 근무자 정보 조회 (memberId → memberName)
        List<Long> memberIds = submissions.stream()
                .map(WorkerSelectSubmission::getMemberId)
                .toList();
        Map<Long, String> memberNameMap = memberRepository.findAllById(memberIds).stream()
                .collect(Collectors.toMap(Member::getId, Member::getName));

        // 7. 백트래킹 알고리즘 실행
        List<ScheduleResultDto> results = scheduleAlgorithmService.execute(
                allTimeDetails,
                unavailableMap,
                memberNameMap,
                weekSchedule.getMinPersonalWorkCount(),
                weekSchedule.getMaxPersonalWorkCount()
        );

        return ScheduleLogicResponse.of(workPlace.getId(), weekSchedule.getId(), owner.getId(), results);
    }

    private Member findActiveMember(Long memberId) {
        return memberRepository.findActiveById(memberId)
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED, "인증 정보가 올바르지 않습니다."));
    }

    private WorkPlace findOwnedActiveWorkPlace(Long workPlaceId, Long ownerMemberId) {
        WorkPlace workPlace = workPlaceRepository
                .findByIdAndStatusAndDeletedAtIsNull(workPlaceId, WorkPlaceStatus.ACTIVE)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "사업장을 찾을 수 없습니다."));

        if (!workPlace.getOwnerMemberId().equals(ownerMemberId)) {
            throw new ApiException(ErrorCode.FORBIDDEN, "권한이 없습니다.");
        }

        return workPlace;
    }

    private WeekSchedule findActiveWeekSchedule(Long weekScheduleId, Long workPlaceId) {
        return weekScheduleRepository
                .findByIdAndWorkPlaceIdAndStatusAndDeletedAtIsNull(weekScheduleId, workPlaceId, WeekScheduleStatus.ACTIVE)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "해당 주차의 스케줄 조건을 찾을 수 없습니다."));
    }
}

