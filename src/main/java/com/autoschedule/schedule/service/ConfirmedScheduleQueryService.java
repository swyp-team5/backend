package com.autoschedule.schedule.service;

import com.autoschedule.global.exception.ApiException;
import com.autoschedule.global.exception.ErrorCode;
import com.autoschedule.member.domain.Member;
import com.autoschedule.member.domain.ProfileImage;
import com.autoschedule.member.domain.ProfileImageStatus;
import com.autoschedule.member.repository.MemberRepository;
import com.autoschedule.member.repository.ProfileImageRepository;
import com.autoschedule.schedule.domain.ConfirmedScheduleAssignment;
import com.autoschedule.schedule.domain.ConfirmedScheduleAssignmentStatus;
import com.autoschedule.schedule.domain.ConfirmedWeekSchedule;
import com.autoschedule.schedule.domain.ConfirmedWeekScheduleStatus;
import com.autoschedule.schedule.dto.ConfirmedScheduleWorkerResponse;
import com.autoschedule.schedule.dto.MyConfirmedScheduleItemResponse;
import com.autoschedule.schedule.dto.MyConfirmedScheduleResponse;
import com.autoschedule.schedule.dto.OwnerConfirmedScheduleResponse;
import com.autoschedule.schedule.dto.OwnerWeeklyConfirmedScheduleDayResponse;
import com.autoschedule.schedule.dto.OwnerWeeklyConfirmedScheduleResponse;
import com.autoschedule.schedule.dto.OwnerWeeklyConfirmedScheduleTimeDetailResponse;
import com.autoschedule.schedule.repository.ConfirmedScheduleAssignmentRepository;
import com.autoschedule.schedule.repository.ConfirmedWeekScheduleRepository;
import com.autoschedule.schedulecondition.domain.Day;
import com.autoschedule.schedulecondition.domain.DayStatus;
import com.autoschedule.schedulecondition.domain.TimeDetail;
import com.autoschedule.schedulecondition.domain.TimeDetailStatus;
import com.autoschedule.schedulecondition.domain.WeekScheduleStatus;
import com.autoschedule.workplace.domain.WorkPlace;
import com.autoschedule.workplace.domain.WorkPlaceStatus;
import com.autoschedule.workplace.repository.WorkPlaceRepository;
import java.time.LocalDate;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 확정 근무 스케줄을 근무자 달력과 사장 근무표 형태로 조회한다.
 */
@Service
@RequiredArgsConstructor
public class ConfirmedScheduleQueryService {

    private final MemberRepository memberRepository;
    private final WorkPlaceRepository workPlaceRepository;
    private final ProfileImageRepository profileImageRepository;
    private final ConfirmedScheduleAssignmentRepository confirmedScheduleAssignmentRepository;
    private final ConfirmedWeekScheduleRepository confirmedWeekScheduleRepository;

    /**
     * 근무자 본인에게 배정된 확정 근무 일정을 기간 기준으로 조회한다.
     */
    @Transactional(readOnly = true)
    public MyConfirmedScheduleResponse getMyConfirmedSchedules(Long memberId, LocalDate from, LocalDate to) {
        validateDateRange(from, to);
        findActiveMember(memberId);

        List<ConfirmedScheduleAssignment> assignments =
                confirmedScheduleAssignmentRepository.findActiveAssignmentsForWorkerCalendar(
                        memberId,
                        from,
                        to,
                        ConfirmedScheduleAssignmentStatus.ACTIVE,
                        ConfirmedWeekScheduleStatus.ACTIVE,
                        DayStatus.ACTIVE,
                        TimeDetailStatus.ACTIVE
                );
        Map<Long, WorkPlace> workPlacesById = findActiveWorkPlacesById(assignments.stream()
                .map(ConfirmedScheduleAssignment::getWorkPlaceId)
                .distinct()
                .toList());

        List<MyConfirmedScheduleItemResponse> schedules = assignments.stream()
                .map(assignment -> MyConfirmedScheduleItemResponse.from(
                        assignment,
                        workPlacesById.get(assignment.getWorkPlaceId())
                ))
                .toList();

        return MyConfirmedScheduleResponse.of(from, to, schedules);
    }

    /**
     * 사장이 소유한 사업장의 기간별 확정 근무표를 조회한다.
     */
    @Transactional(readOnly = true)
    public OwnerConfirmedScheduleResponse getOwnerConfirmedSchedules(
            Long ownerMemberId,
            Long workPlaceId,
            LocalDate from,
            LocalDate to
    ) {
        validateDateRange(from, to);
        findActiveMember(ownerMemberId);
        WorkPlace workPlace = findOwnedActiveWorkPlace(workPlaceId, ownerMemberId);

        List<OwnerWeeklyConfirmedScheduleDayResponse> days = findOwnerConfirmedScheduleDays(
                workPlace.getId(),
                from,
                to
        );

        return OwnerConfirmedScheduleResponse.of(workPlace.getId(), from, to, days);
    }

    /**
     * 사장이 소유한 사업장의 주간 확정 근무표를 조회한다.
     */
    @Transactional(readOnly = true)
    public OwnerWeeklyConfirmedScheduleResponse getOwnerWeeklyConfirmedSchedules(
            Long ownerMemberId,
            Long workPlaceId,
            LocalDate weekStartDate
    ) {
        if (weekStartDate == null) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "조회 시작 날짜는 필수입니다.");
        }

        findActiveMember(ownerMemberId);
        WorkPlace workPlace = findOwnedActiveWorkPlace(workPlaceId, ownerMemberId);
        LocalDate weekEndDate = weekStartDate.plusDays(6);
        ConfirmedWeekSchedule confirmedWeekSchedule =
                confirmedWeekScheduleRepository.findActiveByWorkPlaceIdAndWeekStartDate(
                                workPlace.getId(),
                                weekStartDate,
                                ConfirmedWeekScheduleStatus.ACTIVE,
                                WeekScheduleStatus.ACTIVE,
                                DayStatus.ACTIVE
                        )
                        .orElse(null);

        List<OwnerWeeklyConfirmedScheduleDayResponse> days = findOwnerConfirmedScheduleDays(
                workPlace.getId(),
                weekStartDate,
                weekEndDate
        );

        return OwnerWeeklyConfirmedScheduleResponse.of(
                workPlace.getId(),
                confirmedWeekSchedule == null ? null : confirmedWeekSchedule.getWeekSchedule().getId(),
                confirmedWeekSchedule == null ? null : confirmedWeekSchedule.getId(),
                weekStartDate,
                weekEndDate,
                days
        );
    }

    /**
     * 조회 시작일과 종료일의 필수 여부와 순서를 검증한다.
     */
    private void validateDateRange(LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "조회 시작일과 종료일은 필수입니다.");
        }
        if (from.isAfter(to)) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "조회 시작일은 종료일보다 늦을 수 없습니다.");
        }
    }

    /**
     * 활성 회원을 조회한다.
     */
    private Member findActiveMember(Long memberId) {
        return memberRepository.findActiveById(memberId)
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED, "인증 정보가 올바르지 않습니다."));
    }

    /**
     * 요청자가 소유한 활성 사업장을 조회한다.
     */
    private WorkPlace findOwnedActiveWorkPlace(Long workPlaceId, Long ownerMemberId) {
        return workPlaceRepository.findOwnedActiveById(workPlaceId, ownerMemberId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "사업장을 찾을 수 없습니다."));
    }

    /**
     * 사장용 기간 확정 근무표의 날짜별 응답 목록을 조회하고 조립한다.
     */
    private List<OwnerWeeklyConfirmedScheduleDayResponse> findOwnerConfirmedScheduleDays(
            Long workPlaceId,
            LocalDate from,
            LocalDate to
    ) {
        List<ConfirmedScheduleAssignment> assignments =
                confirmedScheduleAssignmentRepository.findActiveAssignmentsForOwnerWeeklySchedule(
                        workPlaceId,
                        from,
                        to,
                        ConfirmedScheduleAssignmentStatus.ACTIVE,
                        ConfirmedWeekScheduleStatus.ACTIVE,
                        DayStatus.ACTIVE,
                        TimeDetailStatus.ACTIVE
                );

        Map<Long, Member> membersById = findActiveMembersById(assignments.stream()
                .map(ConfirmedScheduleAssignment::getWorkerMemberId)
                .distinct()
                .toList());
        Map<Long, String> profileImageUrlsByMemberId = findProfileImageUrlsByMemberId(membersById.keySet());

        return groupOwnerWeeklyAssignments(
                assignments,
                membersById,
                profileImageUrlsByMemberId
        );
    }

    /**
     * 사업장 ID 목록으로 활성 사업장 맵을 조회한다.
     */
    private Map<Long, WorkPlace> findActiveWorkPlacesById(Collection<Long> workPlaceIds) {
        if (workPlaceIds == null || workPlaceIds.isEmpty()) {
            return Map.of();
        }
        return workPlaceRepository.findByIdInAndStatusAndDeletedAtIsNull(workPlaceIds, WorkPlaceStatus.ACTIVE)
                .stream()
                .collect(Collectors.toMap(WorkPlace::getId, Function.identity()));
    }

    /**
     * 회원 ID 목록으로 활성 회원 맵을 조회한다.
     */
    private Map<Long, Member> findActiveMembersById(Collection<Long> memberIds) {
        if (memberIds == null || memberIds.isEmpty()) {
            return Map.of();
        }
        return memberRepository.findActiveByIdIn(memberIds)
                .stream()
                .collect(Collectors.toMap(Member::getId, Function.identity()));
    }

    /**
     * 회원 ID 목록으로 활성 프로필 이미지 URL 맵을 조회한다.
     */
    private Map<Long, String> findProfileImageUrlsByMemberId(Collection<Long> memberIds) {
        if (memberIds == null || memberIds.isEmpty()) {
            return Map.of();
        }
        return profileImageRepository.findByMember_IdInAndStatusAndDeletedAtIsNull(
                        memberIds,
                        ProfileImageStatus.ACTIVE
                )
                .stream()
                .collect(Collectors.toMap(
                        profileImage -> profileImage.getMember().getId(),
                        ProfileImage::getImageUrl
                ));
    }

    /**
     * 확정 근무 배정 목록을 날짜와 타임별 응답 구조로 묶는다.
     */
    private List<OwnerWeeklyConfirmedScheduleDayResponse> groupOwnerWeeklyAssignments(
            List<ConfirmedScheduleAssignment> assignments,
            Map<Long, Member> membersById,
            Map<Long, String> profileImageUrlsByMemberId
    ) {
        Map<Long, List<ConfirmedScheduleAssignment>> assignmentsByDayId = assignments.stream()
                .collect(Collectors.groupingBy(
                        assignment -> assignment.getDay().getId(),
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        return assignmentsByDayId.values()
                .stream()
                .map(dayAssignments -> createDayResponse(dayAssignments, membersById, profileImageUrlsByMemberId))
                .toList();
    }

    /**
     * 같은 날짜의 배정 목록을 날짜 응답으로 변환한다.
     */
    private OwnerWeeklyConfirmedScheduleDayResponse createDayResponse(
            List<ConfirmedScheduleAssignment> dayAssignments,
            Map<Long, Member> membersById,
            Map<Long, String> profileImageUrlsByMemberId
    ) {
        Day day = dayAssignments.get(0).getDay();
        Map<Long, List<ConfirmedScheduleAssignment>> assignmentsByTimeDetailId = dayAssignments.stream()
                .collect(Collectors.groupingBy(
                        assignment -> assignment.getTimeDetail().getId(),
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        List<OwnerWeeklyConfirmedScheduleTimeDetailResponse> timeDetails = assignmentsByTimeDetailId.values()
                .stream()
                .map(timeDetailAssignments -> createTimeDetailResponse(
                        timeDetailAssignments,
                        membersById,
                        profileImageUrlsByMemberId
                ))
                .toList();

        return OwnerWeeklyConfirmedScheduleDayResponse.from(day, timeDetails);
    }

    /**
     * 같은 근무 타임의 배정 목록을 근무 타임 응답으로 변환한다.
     */
    private OwnerWeeklyConfirmedScheduleTimeDetailResponse createTimeDetailResponse(
            List<ConfirmedScheduleAssignment> timeDetailAssignments,
            Map<Long, Member> membersById,
            Map<Long, String> profileImageUrlsByMemberId
    ) {
        TimeDetail timeDetail = timeDetailAssignments.get(0).getTimeDetail();
        List<ConfirmedScheduleWorkerResponse> workers = timeDetailAssignments.stream()
                .map(assignment -> {
                    Member member = membersById.get(assignment.getWorkerMemberId());
                    return ConfirmedScheduleWorkerResponse.from(
                            member,
                            profileImageUrlsByMemberId.get(assignment.getWorkerMemberId())
                    );
                })
                .toList();

        return OwnerWeeklyConfirmedScheduleTimeDetailResponse.from(timeDetail, workers);
    }
}
