package com.autoschedule.workerselect.service;

import com.autoschedule.crew.domain.Crew;
import com.autoschedule.crew.domain.CrewJoinStatus;
import com.autoschedule.crew.domain.CrewRole;
import com.autoschedule.crew.domain.CrewStatus;
import com.autoschedule.crew.repository.CrewRepository;
import com.autoschedule.global.exception.ApiException;
import com.autoschedule.global.exception.ErrorCode;
import com.autoschedule.member.domain.Member;
import com.autoschedule.member.repository.MemberRepository;
import com.autoschedule.notification.domain.NotificationType;
import com.autoschedule.notification.domain.PushPolicy;
import com.autoschedule.notification.dto.NotificationSendCommand;
import com.autoschedule.notification.service.NotificationCommandService;
import com.autoschedule.schedule.domain.ConfirmedWeekScheduleStatus;
import com.autoschedule.schedule.domain.ScheduleGenerationRunStatus;
import com.autoschedule.schedule.domain.SchedulePreviewStatus;
import com.autoschedule.schedule.repository.ConfirmedWeekScheduleRepository;
import com.autoschedule.schedule.repository.ScheduleGenerationRunRepository;
import com.autoschedule.schedule.repository.SchedulePreviewRepository;
import com.autoschedule.schedulecondition.domain.TimeDetail;
import com.autoschedule.schedulecondition.domain.TimeDetailStatus;
import com.autoschedule.schedulecondition.domain.WeekSchedule;
import com.autoschedule.schedulecondition.domain.WeekScheduleStatus;
import com.autoschedule.schedulecondition.repository.TimeDetailRepository;
import com.autoschedule.schedulecondition.repository.WeekScheduleRepository;
import com.autoschedule.workerselect.domain.WorkerSelectSubmission;
import com.autoschedule.workerselect.domain.WorkerSelectSubmissionRejection;
import com.autoschedule.workerselect.domain.WorkerSelectSubmissionStatus;
import com.autoschedule.workerselect.domain.WorkerUnavailableTimeDetail;
import com.autoschedule.workerselect.dto.WorkerSelectMemberStatusResponse;
import com.autoschedule.workerselect.dto.WorkerSelectRejectionResponse;
import com.autoschedule.workerselect.dto.WorkerSelectRequest;
import com.autoschedule.workerselect.dto.WorkerSelectResponse;
import com.autoschedule.workerselect.dto.WorkerSelectStatusResponse;
import com.autoschedule.workerselect.dto.WorkerSelectTimeDetailResponse;
import com.autoschedule.workerselect.repository.WorkerSelectSubmissionRejectionRepository;
import com.autoschedule.workerselect.repository.WorkerSelectSubmissionRepository;
import com.autoschedule.workerselect.repository.WorkerUnavailableTimeDetailRepository;
import com.autoschedule.workplace.domain.WorkPlace;
import com.autoschedule.workplace.domain.WorkPlaceStatus;
import com.autoschedule.workplace.repository.WorkPlaceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class WorkerSelectService {

    private final MemberRepository memberRepository;
    private final WorkPlaceRepository workPlaceRepository;
    private final CrewRepository crewRepository;
    private final TimeDetailRepository timeDetailRepository;
    private final WeekScheduleRepository weekScheduleRepository;
    private final WorkerSelectSubmissionRepository workerSelectSubmissionRepository;
    private final WorkerUnavailableTimeDetailRepository workerUnavailableTimeDetailRepository;
    private final WorkerSelectSubmissionRejectionRepository workerSelectSubmissionRejectionRepository;
    private final ScheduleGenerationRunRepository scheduleGenerationRunRepository;
    private final SchedulePreviewRepository schedulePreviewRepository;
    private final ConfirmedWeekScheduleRepository confirmedWeekScheduleRepository;
    private final NotificationCommandService notificationCommandService;

    /**
     * 근무자가 선택한 근무 불가 시간대를 저장한다.
     */
    @Transactional
    public WorkerSelectResponse selectWorkerUnavailable(
            Long memberId,
            Long workPlaceId,
            WorkerSelectRequest request
    ) {
        // 1. 회원, 사업장, 주간 스케줄, 크루 소속, 제출 마감일을 검증
        Member member = findActiveMember(memberId);
        WorkPlace workPlace = findActiveWorkPlace(workPlaceId);
        WeekSchedule weekSchedule = findActiveWeekSchedule(request.weekScheduleId(), workPlace.getId());
        validateCrewMember(workPlace.getId(), member.getId());
        validateDueDate(weekSchedule);

        // 2. 중복 제출 검증
        validateAlreadySubmitted(member.getId(), workPlace.getId(), weekSchedule.getId());

        List<Long> timeDetailIds = request.timeDetails() == null ? List.of() : request.timeDetails(); // null이면 빈 리스트로 정규화
        List<Long> uniqueTimeDetailIds = timeDetailIds.stream()
                .distinct()
                .toList();

        // 빈 리스트 제출도 유효한 제출이므로 submission을 먼저 저장하고, 선택 항목이 있을 때만 상세 row를 저장한다.
        // 3. 제출 현황 저장
        WorkerSelectSubmission submission;
        try {
            submission = workerSelectSubmissionRepository.save(
                    WorkerSelectSubmission.create(workPlace.getId(), weekSchedule.getId(), member.getId())
            );
        } catch (DataIntegrityViolationException e) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "이미 근무 불가 정보를 제출했습니다.");
        }

        // 4. 빈 리스트면 submission만 저장하고 종료한다. 이 경우 time_detail 상세 row는 생성하지 않는다.
        if (uniqueTimeDetailIds.isEmpty()) {
            return WorkerSelectResponse.of(workPlace.getId(), member.getId(), List.of());
        }

        // 5. timeDetailId가 해당 사업장과 주간 스케줄에 속하는 활성 값인지 검증
        List<TimeDetail> timeDetails = timeDetailRepository
                .findAllByIdInAndDay_WeekSchedule_IdAndDay_WeekSchedule_WorkPlace_IdAndStatusAndDeletedAtIsNull(
                        uniqueTimeDetailIds,
                        weekSchedule.getId(),
                        workPlace.getId(),
                        TimeDetailStatus.ACTIVE
                );

        if (timeDetails.size() != uniqueTimeDetailIds.size()) {
            throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "조회할 수 있는 근무 시간대 정보를 찾을 수 없습니다.");
        }

        // 6. 불가 타임 목록 저장
        validateSelectableTimeDetails(timeDetails);

        List<WorkerUnavailableTimeDetail> toSave = timeDetails.stream()
                .map(td -> WorkerUnavailableTimeDetail.create(submission, td))
                .toList();

        workerUnavailableTimeDetailRepository.saveAll(toSave);

        // 7. 응답 반환
        List<WorkerSelectTimeDetailResponse> timeDetailResponses = timeDetails.stream()
                .map(WorkerSelectTimeDetailResponse::from)
                .toList();

        return WorkerSelectResponse.of(workPlace.getId(), member.getId(), timeDetailResponses);
    }

    /**
     * 사장이 사업장 근무자들의 근무 불가 제출 여부를 조회한다.
     */
    @Transactional(readOnly = true)
    public WorkerSelectStatusResponse getWorkerSelectStatus(
            Long ownerMemberId,
            Long workPlaceId,
            Long weekScheduleId
    ) {
        // 1. 사장과 사업장 검증
        Member owner = findActiveMember(ownerMemberId);
        WorkPlace workPlace = findOwnedActiveWorkPlace(workPlaceId, owner.getId());
        WeekSchedule weekSchedule = findActiveWeekSchedule(weekScheduleId, workPlace.getId());

        // 2. 사업장의 승인 근무자 크루 목록 조회
        List<Crew> crews = crewRepository.findByWorkPlace_IdAndJoinStatusAndCrewRoleAndStatus(
                workPlace.getId(),
                CrewJoinStatus.APPROVED,
                CrewRole.WORKER,
                CrewStatus.ACTIVE
        );

        List<Long> crewMemberIds = crews.stream()
                .map(crew -> crew.getMember().getId())
                .toList();

        // 3. 제출 현황 테이블에서 제출 완료한 멤버 ID 목록 조회
        List<Long> submittedMemberIds = workerSelectSubmissionRepository
                .findByWorkPlaceIdAndWeekScheduleIdAndMemberIdInAndStatusAndDeletedAtIsNull(
                        workPlace.getId(),
                        weekSchedule.getId(),
                        crewMemberIds,
                        WorkerSelectSubmissionStatus.ACTIVE
                )
                .stream()
                .map(WorkerSelectSubmission::getMemberId)
                .toList();

        // 4. 제출 여부 포함 응답 생성
        List<WorkerSelectMemberStatusResponse> workers = crews.stream()
                .map(crew -> WorkerSelectMemberStatusResponse.from(crew, submittedMemberIds))
                .toList();

        return WorkerSelectStatusResponse.of(workPlace.getId(), weekSchedule.getId(), workers);
    }

    /**
     * 사장이 근무자의 근무 불가 제출 건을 반려한다.
     * 반려되면 제출 건과 연관된 근무 불가 time_detail이 모두 물리 삭제되어 근무자가 재제출할 수 있게 된다.
     */
    @Transactional
    public WorkerSelectRejectionResponse rejectWorkerSelect(
            Long ownerMemberId,
            Long workPlaceId,
            Long weekScheduleId,
            Long memberId
    ) {
        // 1. 사장과 사업장, 주간 스케줄 검증
        Member owner = findActiveMember(ownerMemberId);
        WorkPlace workPlace = findOwnedActiveWorkPlace(workPlaceId, owner.getId());
        WeekSchedule weekSchedule = findActiveWeekSchedule(weekScheduleId, workPlace.getId());

        // 2. 제출 마감일이 지난 경우 반려를 막는다.
        //    (마감 후 반려하면 근무자가 재제출할 수단이 없어 근무 불가 정보 없이 스케줄이 생성되는 문제 방지)
        validateDueDate(weekSchedule);

        // 3. 이미 자동 생성/미리보기/확정이 진행된 주간 스케줄은 반려를 막는다.
        //    (반려로 제출 데이터가 바뀌어도 이미 생성된 스케줄에는 반영되지 않아 데이터 불일치 발생 방지)
        validateScheduleNotGenerated(weekSchedule.getId());

        // 4. 반려 대상 회원이 해당 사업장의 승인된 근무자 크루인지 검증
        validateTargetWorkerCrewMember(workPlace.getId(), memberId);

        // 5. 반려 대상 제출 건 조회
        WorkerSelectSubmission submission = workerSelectSubmissionRepository
                .findByWorkPlaceIdAndWeekScheduleIdAndMemberIdAndStatusAndDeletedAtIsNull(
                        workPlace.getId(),
                        weekSchedule.getId(),
                        memberId,
                        WorkerSelectSubmissionStatus.ACTIVE
                )
                .orElseThrow(() -> new ApiException(
                        ErrorCode.RESOURCE_NOT_FOUND,
                        "해당 근무자의 제출 정보를 찾을 수 없습니다."
                ));

        // 6. 물리 삭제 전에 반려 이력을 감사 로그 테이블에 남긴다.
        workerSelectSubmissionRejectionRepository.save(
                WorkerSelectSubmissionRejection.create(
                        workPlace.getId(),
                        weekSchedule.getId(),
                        memberId,
                        submission.getId(),
                        owner.getId()
                )
        );

        // 7. 제출 건과 연관된 근무 불가 time_detail을 물리 삭제한 뒤 제출 건 자체도 물리 삭제한다.
        // 소프트 삭제로 처리하면 (work_place_id, week_schedule_id, member_id) 유니크 제약에 걸려
        // 재제출 시 DB 유니크 충돌이 발생하므로 물리 삭제로 유니크 제약을 비워준다.
        workerUnavailableTimeDetailRepository.deleteBySubmissionId(submission.getId());
        workerSelectSubmissionRepository.delete(submission);

        // 8. 근무자에게 재제출을 유도하는 알림 발송
        notifyWorkerRejected(workPlace.getId(), weekSchedule.getId(), memberId);

        return WorkerSelectRejectionResponse.of(workPlace.getId(), weekSchedule.getId(), memberId);
    }

    /**
     * 이미 자동 생성 실행/미리보기/확정 스케줄이 존재하는 주간 스케줄인지 확인한다.
     */
    private void validateScheduleNotGenerated(Long weekScheduleId) {
        boolean hasGenerationRun = scheduleGenerationRunRepository
                .existsByWeekSchedule_IdAndStatusAndDeletedAtIsNull(
                        weekScheduleId,
                        ScheduleGenerationRunStatus.GENERATED
                );

        boolean hasPreview = schedulePreviewRepository
                .existsByWeekSchedule_IdAndStatusAndDeletedAtIsNull(
                        weekScheduleId,
                        SchedulePreviewStatus.ACTIVE
                );

        boolean hasConfirmedSchedule = confirmedWeekScheduleRepository
                .existsByWeekSchedule_IdAndStatusAndDeletedAtIsNull(
                        weekScheduleId,
                        ConfirmedWeekScheduleStatus.ACTIVE
                );

        if (hasGenerationRun || hasPreview || hasConfirmedSchedule) {
            throw new ApiException(
                    ErrorCode.VALIDATION_FAILED,
                    "이미 자동 스케줄 생성이 진행된 주간에는 제출을 반려할 수 없습니다."
            );
        }
    }

    /**
     * 근무 불가 제출이 반려되었음을 근무자에게 알린다.
     */
    private void notifyWorkerRejected(Long workPlaceId, Long weekScheduleId, Long memberId) {
        notificationCommandService.sendToMember(
                memberId,
                new NotificationSendCommand(
                        NotificationType.WORKER_SELECT_REJECTED,
                        PushPolicy.PUSH,
                        "근무 불가 제출이 반려되었습니다.",
                        "사장님이 근무 불가 제출을 반려했습니다. 다시 제출해 주세요.",
                        Map.of(
                                "workPlaceId", String.valueOf(workPlaceId),
                                "weekScheduleId", String.valueOf(weekScheduleId)
                        )
                )
        );
    }

    /**
     * 이미 근무 불가 정보를 제출했는지 확인한다.
     */
    private void validateAlreadySubmitted(Long memberId, Long workPlaceId, Long weekScheduleId) {
        boolean exists = workerSelectSubmissionRepository
                .existsByWorkPlaceIdAndWeekScheduleIdAndMemberIdAndStatusAndDeletedAtIsNull(
                        workPlaceId,
                        weekScheduleId,
                        memberId,
                        WorkerSelectSubmissionStatus.ACTIVE
                );

        if (exists) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "이미 근무 불가 정보를 제출했습니다.");
        }
    }

    /**
     * 근무자가 선택한 time_detail이 휴일 또는 근무 제출 불가 요일에 속하지 않는지 검증한다.
     */
    private void validateSelectableTimeDetails(List<TimeDetail> timeDetails) {
        boolean hasRestrictedDay = timeDetails.stream()
                .anyMatch(timeDetail -> timeDetail.getDay().isHolidayStatus()
                        || timeDetail.getDay().isSelectLimitStatus());

        if (hasRestrictedDay) {
            throw new ApiException(
                    ErrorCode.VALIDATION_FAILED,
                    "매장 휴일 또는 근무 제출 불가 요일의 근무 상세 시간은 제출할 수 없습니다."
            );
        }
    }

    /**
     * 제출 마감일이 지나면 근무 불가 시간 제출을 차단한다.
     */
    private void validateDueDate(WeekSchedule weekSchedule) {
        if (LocalDate.now().isAfter(weekSchedule.getDueDate())) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "근무 불가 제출 마감 기한이 지났습니다.");
        }
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
        return weekScheduleRepository.findByIdAndWorkPlaceIdAndStatusAndDeletedAtIsNull(
                        weekScheduleId,
                        workPlaceId,
                        WeekScheduleStatus.ACTIVE
                )
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "해당 주간의 스케줄 조건을 찾을 수 없습니다."));
    }

    private void validateCrewMember(Long workPlaceId, Long memberId) {
        boolean exists = crewRepository.existsByMember_IdAndWorkPlace_IdAndJoinStatusAndCrewRoleAndStatus(
                memberId,
                workPlaceId,
                CrewJoinStatus.APPROVED,
                CrewRole.WORKER,
                CrewStatus.ACTIVE
        );

        if (!exists) {
            throw new ApiException(ErrorCode.FORBIDDEN, "이 회원은 해당 사업장의 크루가 아닙니다.");
        }
    }

    /**
     * 반려 대상 회원이 해당 사업장의 승인된 근무자 크루인지 확인한다.
     */
    private void validateTargetWorkerCrewMember(Long workPlaceId, Long memberId) {
        boolean exists = crewRepository.existsByMember_IdAndWorkPlace_IdAndJoinStatusAndCrewRoleAndStatus(
                memberId,
                workPlaceId,
                CrewJoinStatus.APPROVED,
                CrewRole.WORKER,
                CrewStatus.ACTIVE
        );

        if (!exists) {
            throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "해당 사업장의 근무자를 찾을 수 없습니다.");
        }
    }

    private WorkPlace findActiveWorkPlace(Long workPlaceId) {
        return workPlaceRepository
                .findByIdAndStatusAndDeletedAtIsNull(workPlaceId, WorkPlaceStatus.ACTIVE)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "사업장을 찾을 수 없습니다."));
    }
}
