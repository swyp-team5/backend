package com.autoschedule.workchange.service;

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
import com.autoschedule.schedule.domain.ConfirmedScheduleAssignment;
import com.autoschedule.schedule.domain.ConfirmedScheduleAssignmentStatus;
import com.autoschedule.schedule.domain.ConfirmedWeekScheduleStatus;
import com.autoschedule.schedule.repository.ConfirmedScheduleAssignmentRepository;
import com.autoschedule.schedulecondition.domain.DayStatus;
import com.autoschedule.schedulecondition.domain.TimeDetailStatus;
import com.autoschedule.workchange.domain.WorkChangeRequest;
import com.autoschedule.workchange.domain.WorkChangeRequestStatus;
import com.autoschedule.workchange.domain.WorkChangeRequestType;
import com.autoschedule.workchange.dto.ShiftSwapWorkChangeRequest;
import com.autoschedule.workchange.dto.SubstituteWorkChangeRequest;
import com.autoschedule.workchange.dto.WorkChangeRejectionRequest;
import com.autoschedule.workchange.dto.WorkChangeRequestPageResponse;
import com.autoschedule.workchange.dto.WorkChangeRequestResponse;
import com.autoschedule.workchange.dto.WorkChangeRequestScope;
import com.autoschedule.workchange.repository.WorkChangeRequestRepository;
import com.autoschedule.workplace.domain.WorkPlace;
import com.autoschedule.workplace.domain.WorkPlaceStatus;
import com.autoschedule.workplace.repository.WorkPlaceRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 확정 근무에 대한 교대/대타 요청 생성과 상태 변경 유스케이스를 처리한다.
 */
@Service
@RequiredArgsConstructor
public class WorkChangeRequestService {

    private static final List<WorkChangeRequestStatus> IN_PROGRESS_STATUSES = List.of(
            WorkChangeRequestStatus.REQUESTED,
            WorkChangeRequestStatus.ACCEPTED_BY_TARGET
    );

    private final MemberRepository memberRepository;
    private final WorkPlaceRepository workPlaceRepository;
    private final CrewRepository crewRepository;
    private final ConfirmedScheduleAssignmentRepository confirmedScheduleAssignmentRepository;
    private final WorkChangeRequestRepository workChangeRequestRepository;
    private final NotificationCommandService notificationCommandService;

    /**
     * 요청 근무자의 확정 근무를 대상 근무자에게 넘기는 대타 요청을 생성한다.
     */
    @Transactional
    public WorkChangeRequestResponse createSubstituteRequest(
            Long requesterMemberId,
            Long workPlaceId,
            SubstituteWorkChangeRequest request
    ) {
        findActiveMember(requesterMemberId);
        WorkPlace workPlace = findActiveWorkPlace(workPlaceId);
        validateApprovedWorker(requesterMemberId, workPlaceId);
        validateApprovedWorker(request.targetMemberId(), workPlaceId);
        validateDifferentWorkers(requesterMemberId, request.targetMemberId());

        ConfirmedScheduleAssignment requestAssignment = findActiveAssignment(
                request.requestAssignmentId(),
                workPlaceId
        );
        validateRequesterOwnsAssignment(requesterMemberId, requestAssignment);
        validateFutureAssignment(requestAssignment);
        validateNoInProgressRequest(requestAssignment.getId());
        validateTargetHasNoOverlappingAssignment(workPlaceId, request.targetMemberId(), requestAssignment);

        WorkChangeRequest savedRequest = workChangeRequestRepository.save(WorkChangeRequest.createSubstitute(
                workPlaceId,
                requesterMemberId,
                request.targetMemberId(),
                requestAssignment,
                request.reason()
        ));
        notifyWorkChangeRequested(savedRequest, workPlace);
        return WorkChangeRequestResponse.from(savedRequest);
    }

    /**
     * 요청 근무자와 대상 근무자의 확정 근무를 서로 바꾸는 교대 요청을 생성한다.
     */
    @Transactional
    public WorkChangeRequestResponse createShiftSwapRequest(
            Long requesterMemberId,
            Long workPlaceId,
            ShiftSwapWorkChangeRequest request
    ) {
        findActiveMember(requesterMemberId);
        WorkPlace workPlace = findActiveWorkPlace(workPlaceId);
        validateApprovedWorker(requesterMemberId, workPlaceId);

        ConfirmedScheduleAssignment requestAssignment = findActiveAssignment(
                request.requestAssignmentId(),
                workPlaceId
        );
        ConfirmedScheduleAssignment targetAssignment = findActiveAssignment(
                request.targetAssignmentId(),
                workPlaceId
        );
        validateRequesterOwnsAssignment(requesterMemberId, requestAssignment);
        validateDifferentAssignments(requestAssignment, targetAssignment);
        Long targetMemberId = targetAssignment.getWorkerMemberId();
        validateDifferentWorkers(requesterMemberId, targetMemberId);
        validateApprovedWorker(targetMemberId, workPlaceId);
        validateFutureAssignment(requestAssignment);
        validateFutureAssignment(targetAssignment);
        validateNoInProgressRequest(requestAssignment.getId());
        validateNoInProgressRequest(targetAssignment.getId());

        WorkChangeRequest savedRequest = workChangeRequestRepository.save(WorkChangeRequest.createShiftSwap(
                workPlaceId,
                requesterMemberId,
                targetMemberId,
                requestAssignment,
                targetAssignment,
                request.reason()
        ));
        notifyWorkChangeRequested(savedRequest, workPlace);
        return WorkChangeRequestResponse.from(savedRequest);
    }

    /**
     * 근무자가 자신이 보낸 요청 또는 받은 요청 목록을 조회한다.
     */
    @Transactional(readOnly = true)
    public WorkChangeRequestPageResponse getWorkerRequests(
            Long memberId,
            Long workPlaceId,
            WorkChangeRequestScope scope,
            int page,
            int size
    ) {
        findActiveMember(memberId);
        validateActiveWorkPlace(workPlaceId);
        validateApprovedWorker(memberId, workPlaceId);
        PageRequest pageRequest = createPageRequest(page, size);
        Page<WorkChangeRequest> requests = switch (scope) {
            case SENT -> workChangeRequestRepository.findByWorkPlaceIdAndRequesterMemberIdAndDeletedAtIsNull(
                    workPlaceId,
                    memberId,
                    pageRequest
            );
            case RECEIVED -> workChangeRequestRepository.findByWorkPlaceIdAndTargetMemberIdAndDeletedAtIsNull(
                    workPlaceId,
                    memberId,
                    pageRequest
            );
            case ALL -> workChangeRequestRepository.findWorkerRelatedRequests(workPlaceId, memberId, pageRequest);
        };
        return WorkChangeRequestPageResponse.from(requests);
    }

    /**
     * 사장님이 사업장의 전체 교대/대타 요청 목록을 조회한다.
     */
    @Transactional(readOnly = true)
    public WorkChangeRequestPageResponse getOwnerRequests(Long ownerMemberId, Long workPlaceId, int page, int size) {
        findActiveMember(ownerMemberId);
        WorkPlace workPlace = findOwnedActiveWorkPlace(ownerMemberId, workPlaceId);
        return WorkChangeRequestPageResponse.from(workChangeRequestRepository.findByWorkPlaceIdAndDeletedAtIsNull(
                workPlace.getId(),
                createPageRequest(page, size)
        ));
    }

    /**
     * 대상 근무자가 교대/대타 요청을 수락한다.
     */
    @Transactional
    public WorkChangeRequestResponse acceptByTarget(Long targetMemberId, Long workPlaceId, Long requestId) {
        findActiveMember(targetMemberId);
        validateApprovedWorker(targetMemberId, workPlaceId);
        WorkChangeRequest request = findRequest(requestId, workPlaceId);
        validateTargetMember(targetMemberId, request);
        validateStatus(request, WorkChangeRequestStatus.REQUESTED, "요청 상태에서만 수락할 수 있습니다.");
        request.acceptByTarget(LocalDateTime.now());
        WorkPlace workPlace = findActiveWorkPlace(workPlaceId);
        notifyOwnerTargetAccepted(request, workPlace);
        return WorkChangeRequestResponse.from(request);
    }

    /**
     * 대상 근무자가 교대/대타 요청을 거절한다.
     */
    @Transactional
    public WorkChangeRequestResponse rejectByTarget(
            Long targetMemberId,
            Long workPlaceId,
            Long requestId,
            WorkChangeRejectionRequest rejectionRequest
    ) {
        findActiveMember(targetMemberId);
        validateApprovedWorker(targetMemberId, workPlaceId);
        WorkChangeRequest request = findRequest(requestId, workPlaceId);
        validateTargetMember(targetMemberId, request);
        validateStatus(request, WorkChangeRequestStatus.REQUESTED, "요청 상태에서만 거절할 수 있습니다.");
        request.rejectByTarget(rejectionRequest.reason(), LocalDateTime.now());
        notifyRequesterTargetRejected(request);
        return WorkChangeRequestResponse.from(request);
    }

    /**
     * 요청자가 대상 근무자 응답 전 교대/대타 요청을 취소한다.
     */
    @Transactional
    public WorkChangeRequestResponse cancelRequest(Long requesterMemberId, Long workPlaceId, Long requestId) {
        findActiveMember(requesterMemberId);
        validateApprovedWorker(requesterMemberId, workPlaceId);
        WorkChangeRequest request = findRequest(requestId, workPlaceId);
        validateRequesterMember(requesterMemberId, request);
        validateStatus(request, WorkChangeRequestStatus.REQUESTED, "요청 상태에서만 취소할 수 있습니다.");
        request.cancel(LocalDateTime.now());
        return WorkChangeRequestResponse.from(request);
    }

    /**
     * 사장님이 대상 근무자가 수락한 요청을 최종 승인하고 확정 근무 배정에 반영한다.
     */
    @Transactional
    public WorkChangeRequestResponse approveByOwner(Long ownerMemberId, Long workPlaceId, Long requestId) {
        findActiveMember(ownerMemberId);
        findOwnedActiveWorkPlace(ownerMemberId, workPlaceId);
        WorkChangeRequest request = findRequest(requestId, workPlaceId);
        validateStatus(request, WorkChangeRequestStatus.ACCEPTED_BY_TARGET, "대상 근무자가 수락한 요청만 승인할 수 있습니다.");
        reflectApprovedRequest(request);
        request.approveByOwner(ownerMemberId, LocalDateTime.now());
        notifyWorkersOwnerApproved(request);
        return WorkChangeRequestResponse.from(request);
    }

    /**
     * 사장님이 대상 근무자가 수락한 요청을 최종 거절한다.
     */
    @Transactional
    public WorkChangeRequestResponse rejectByOwner(
            Long ownerMemberId,
            Long workPlaceId,
            Long requestId,
            WorkChangeRejectionRequest rejectionRequest
    ) {
        findActiveMember(ownerMemberId);
        findOwnedActiveWorkPlace(ownerMemberId, workPlaceId);
        WorkChangeRequest request = findRequest(requestId, workPlaceId);
        validateStatus(request, WorkChangeRequestStatus.ACCEPTED_BY_TARGET, "대상 근무자가 수락한 요청만 거절할 수 있습니다.");
        request.rejectByOwner(ownerMemberId, rejectionRequest.reason(), LocalDateTime.now());
        notifyWorkersOwnerRejected(request);
        return WorkChangeRequestResponse.from(request);
    }

    /**
     * 활성 회원을 조회한다.
     */
    private Member findActiveMember(Long memberId) {
        return memberRepository.findActiveById(memberId)
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED, "인증 정보가 올바르지 않습니다."));
    }

    /**
     * 활성 사업장인지 확인한다.
     */
    private void validateActiveWorkPlace(Long workPlaceId) {
        findActiveWorkPlace(workPlaceId);
    }

    /**
     * 활성 사업장을 조회한다.
     */
    private WorkPlace findActiveWorkPlace(Long workPlaceId) {
        return workPlaceRepository.findByIdAndStatusAndDeletedAtIsNull(workPlaceId, WorkPlaceStatus.ACTIVE)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "사업장을 찾을 수 없습니다."));
    }

    /**
     * 사장 소유의 활성 사업장을 조회한다.
     */
    private WorkPlace findOwnedActiveWorkPlace(Long ownerMemberId, Long workPlaceId) {
        return workPlaceRepository.findOwnedActiveById(workPlaceId, ownerMemberId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "사업장을 찾을 수 없습니다."));
    }

    /**
     * 해당 회원이 사업장의 승인된 활성 근무자인지 확인한다.
     */
    private void validateApprovedWorker(Long memberId, Long workPlaceId) {
        boolean exists = crewRepository.existsByMember_IdAndWorkPlace_IdAndJoinStatusAndCrewRoleAndStatus(
                memberId,
                workPlaceId,
                CrewJoinStatus.APPROVED,
                CrewRole.WORKER,
                CrewStatus.ACTIVE
        );
        if (!exists) {
            throw new ApiException(ErrorCode.FORBIDDEN, "해당 사업장의 승인된 근무자만 요청할 수 있습니다.");
        }
    }

    /**
     * 자기 자신에게 대타 또는 교대를 요청하지 못하도록 검증한다.
     */
    private void validateDifferentWorkers(Long requesterMemberId, Long targetMemberId) {
        if (requesterMemberId.equals(targetMemberId)) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "자기 자신에게 교대/대타를 요청할 수 없습니다.");
        }
    }

    /**
     * 같은 확정 근무 배정을 교대 대상으로 지정하지 못하도록 검증한다.
     */
    private void validateDifferentAssignments(
            ConfirmedScheduleAssignment requestAssignment,
            ConfirmedScheduleAssignment targetAssignment
    ) {
        if (requestAssignment.getId().equals(targetAssignment.getId())) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "같은 근무 배정으로 교대를 요청할 수 없습니다.");
        }
    }

    /**
     * 근무 변경 요청에 사용할 활성 확정 근무 배정을 조회한다.
     */
    private ConfirmedScheduleAssignment findActiveAssignment(Long assignmentId, Long workPlaceId) {
        return confirmedScheduleAssignmentRepository.findActiveAssignmentForWorkChange(
                        assignmentId,
                        workPlaceId,
                        ConfirmedScheduleAssignmentStatus.ACTIVE,
                        ConfirmedWeekScheduleStatus.ACTIVE,
                        DayStatus.ACTIVE,
                        TimeDetailStatus.ACTIVE
                )
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "확정 근무 배정을 찾을 수 없습니다."));
    }

    /**
     * 요청자가 본인의 확정 근무만 대타 요청할 수 있도록 검증한다.
     */
    private void validateRequesterOwnsAssignment(Long requesterMemberId, ConfirmedScheduleAssignment assignment) {
        if (!assignment.getWorkerMemberId().equals(requesterMemberId)) {
            throw new ApiException(ErrorCode.FORBIDDEN, "본인의 확정 근무만 대타 요청할 수 있습니다.");
        }
    }

    /**
     * 이미 지난 근무에 대한 교대/대타 요청을 막는다.
     */
    private void validateFutureAssignment(ConfirmedScheduleAssignment assignment) {
        if (assignment.getDay().getDate().isBefore(LocalDate.now())) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "이미 지난 근무는 교대/대타를 요청할 수 없습니다.");
        }
    }

    /**
     * 같은 근무 배정에 대한 처리 중인 요청을 중복 생성하지 않도록 검증한다.
     */
    private void validateNoInProgressRequest(Long requestAssignmentId) {
        boolean exists = workChangeRequestRepository.existsByRequestAssignment_IdAndStatusInAndDeletedAtIsNull(
                requestAssignmentId,
                IN_PROGRESS_STATUSES
        ) || workChangeRequestRepository.existsByTargetAssignment_IdAndStatusInAndDeletedAtIsNull(
                requestAssignmentId,
                IN_PROGRESS_STATUSES
        );
        if (exists) {
            throw new ApiException(ErrorCode.CONFLICT, "이미 처리 중인 교대/대타 요청이 있습니다.");
        }
    }

    /**
     * 대타 대상 근무자가 같은 날짜와 시간대에 이미 근무 중인지 확인한다.
     */
    private void validateTargetHasNoOverlappingAssignment(
            Long workPlaceId,
            Long targetMemberId,
            ConfirmedScheduleAssignment requestAssignment
    ) {
        List<ConfirmedScheduleAssignment> overlappingAssignments =
                confirmedScheduleAssignmentRepository.findOverlappingActiveAssignments(
                        workPlaceId,
                        targetMemberId,
                        requestAssignment.getDay().getDate(),
                        requestAssignment.getTimeDetail().getStartTime(),
                        requestAssignment.getTimeDetail().getCloseTime(),
                        ConfirmedScheduleAssignmentStatus.ACTIVE,
                        ConfirmedWeekScheduleStatus.ACTIVE,
                        DayStatus.ACTIVE,
                        TimeDetailStatus.ACTIVE
                );
        if (!overlappingAssignments.isEmpty()) {
            throw new ApiException(ErrorCode.CONFLICT, "대상 근무자가 해당 시간에 이미 근무 중입니다.");
        }
    }

    /**
     * 요청 ID와 사업장 ID로 교대/대타 요청을 조회한다.
     */
    private WorkChangeRequest findRequest(Long requestId, Long workPlaceId) {
        return workChangeRequestRepository.findByIdAndWorkPlaceIdWithAssignments(requestId, workPlaceId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "교대/대타 요청을 찾을 수 없습니다."));
    }

    /**
     * 현재 요청 상태가 기대 상태인지 확인한다.
     */
    private void validateStatus(
            WorkChangeRequest request,
            WorkChangeRequestStatus expectedStatus,
            String message
    ) {
        if (request.getStatus() != expectedStatus) {
            throw new ApiException(ErrorCode.CONFLICT, message);
        }
    }

    /**
     * 요청 처리 주체가 대상 근무자인지 확인한다.
     */
    private void validateTargetMember(Long targetMemberId, WorkChangeRequest request) {
        if (!request.getTargetMemberId().equals(targetMemberId)) {
            throw new ApiException(ErrorCode.FORBIDDEN, "요청 대상 근무자만 처리할 수 있습니다.");
        }
    }

    /**
     * 요청 처리 주체가 요청자인지 확인한다.
     */
    private void validateRequesterMember(Long requesterMemberId, WorkChangeRequest request) {
        if (!request.getRequesterMemberId().equals(requesterMemberId)) {
            throw new ApiException(ErrorCode.FORBIDDEN, "요청자만 취소할 수 있습니다.");
        }
    }

    /**
     * 승인된 교대/대타 요청을 확정 근무 배정에 반영한다.
     */
    private void reflectApprovedRequest(WorkChangeRequest request) {
        if (request.getRequestType() == WorkChangeRequestType.SUBSTITUTE) {
            reflectSubstitute(request);
            return;
        }
        reflectShiftSwap(request);
    }

    /**
     * 대타 승인 결과를 기존 배정 삭제와 대상 근무자 신규 배정으로 반영한다.
     */
    private void reflectSubstitute(WorkChangeRequest request) {
        ConfirmedScheduleAssignment requestAssignment = request.getRequestAssignment();
        validateActiveAssignmentStillAvailable(requestAssignment);
        validateTargetHasNoOverlappingAssignment(request.getWorkPlaceId(), request.getTargetMemberId(), requestAssignment);

        requestAssignment.markDeleted(LocalDateTime.now());
        confirmedScheduleAssignmentRepository.save(ConfirmedScheduleAssignment.create(
                requestAssignment.getConfirmedWeekSchedule(),
                requestAssignment.getWorkPlaceId(),
                requestAssignment.getWeekSchedule(),
                requestAssignment.getDay(),
                requestAssignment.getTimeDetail(),
                request.getTargetMemberId()
        ));
    }

    /**
     * 교대 승인 결과를 두 기존 배정 삭제와 서로 바뀐 신규 배정으로 반영한다.
     */
    private void reflectShiftSwap(WorkChangeRequest request) {
        ConfirmedScheduleAssignment requestAssignment = request.getRequestAssignment();
        ConfirmedScheduleAssignment targetAssignment = request.getTargetAssignment();
        validateActiveAssignmentStillAvailable(requestAssignment);
        validateActiveAssignmentStillAvailable(targetAssignment);
        validateRequesterOwnsAssignment(request.getRequesterMemberId(), requestAssignment);
        validateTargetOwnsAssignment(request.getTargetMemberId(), targetAssignment);

        LocalDateTime now = LocalDateTime.now();
        requestAssignment.markDeleted(now);
        targetAssignment.markDeleted(now);
        confirmedScheduleAssignmentRepository.save(ConfirmedScheduleAssignment.create(
                requestAssignment.getConfirmedWeekSchedule(),
                requestAssignment.getWorkPlaceId(),
                requestAssignment.getWeekSchedule(),
                requestAssignment.getDay(),
                requestAssignment.getTimeDetail(),
                request.getTargetMemberId()
        ));
        confirmedScheduleAssignmentRepository.save(ConfirmedScheduleAssignment.create(
                targetAssignment.getConfirmedWeekSchedule(),
                targetAssignment.getWorkPlaceId(),
                targetAssignment.getWeekSchedule(),
                targetAssignment.getDay(),
                targetAssignment.getTimeDetail(),
                request.getRequesterMemberId()
        ));
    }

    /**
     * 확정 근무 배정이 승인 시점에도 유효한 활성 상태인지 확인한다.
     */
    private void validateActiveAssignmentStillAvailable(ConfirmedScheduleAssignment assignment) {
        if (assignment == null
                || assignment.getStatus() != ConfirmedScheduleAssignmentStatus.ACTIVE
                || assignment.getDeletedAt() != null) {
            throw new ApiException(ErrorCode.CONFLICT, "확정 근무 배정 상태가 변경되어 요청을 처리할 수 없습니다.");
        }
        validateFutureAssignment(assignment);
    }

    /**
     * 대상 근무자가 대상 확정 근무를 소유하는지 확인한다.
     */
    private void validateTargetOwnsAssignment(Long targetMemberId, ConfirmedScheduleAssignment assignment) {
        if (!assignment.getWorkerMemberId().equals(targetMemberId)) {
            throw new ApiException(ErrorCode.FORBIDDEN, "대상 근무자의 확정 근무만 교대 요청할 수 있습니다.");
        }
    }

    /**
     * 페이지 번호와 크기를 검증하고 최신순 페이지 요청을 생성한다.
     */
    private PageRequest createPageRequest(int page, int size) {
        if (page < 0) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "페이지 번호는 0 이상이어야 합니다.");
        }
        if (size < 1 || size > 100) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "페이지 크기는 1 이상 100 이하여야 합니다.");
        }
        return PageRequest.of(
                page,
                size,
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))
        );
    }

    /**
     * 대상 근무자에게 새 교대/대타 요청 알림을 보낸다.
     */
    private void notifyWorkChangeRequested(WorkChangeRequest request, WorkPlace workPlace) {
        notificationCommandService.sendToMember(
                request.getTargetMemberId(),
                createNotificationCommand(
                        NotificationType.WORK_CHANGE_REQUESTED,
                        "교대/대타 요청이 도착했습니다.",
                        workPlace.getName() + "에서 근무 변경 요청이 도착했습니다.",
                        request
                )
        );
    }

    /**
     * 대상 근무자의 수락 사실을 사장님에게 알린다.
     */
    private void notifyOwnerTargetAccepted(WorkChangeRequest request, WorkPlace workPlace) {
        notificationCommandService.sendToMember(
                workPlace.getOwnerMemberId(),
                createNotificationCommand(
                        NotificationType.WORK_CHANGE_TARGET_ACCEPTED,
                        "교대/대타 요청이 수락되었습니다.",
                        "근무자가 교대/대타 요청을 수락했습니다. 최종 승인해 주세요.",
                        request
                )
        );
    }

    /**
     * 대상 근무자 거절 사실을 요청자에게 알린다.
     */
    private void notifyRequesterTargetRejected(WorkChangeRequest request) {
        notificationCommandService.sendToMember(
                request.getRequesterMemberId(),
                createNotificationCommand(
                        NotificationType.WORK_CHANGE_TARGET_REJECTED,
                        "교대/대타 요청이 거절되었습니다.",
                        "대상 근무자가 교대/대타 요청을 거절했습니다.",
                        request
                )
        );
    }

    /**
     * 사장 최종 승인 사실을 요청자와 대상 근무자에게 알린다.
     */
    private void notifyWorkersOwnerApproved(WorkChangeRequest request) {
        notificationCommandService.sendToMembers(
                List.of(request.getRequesterMemberId(), request.getTargetMemberId()),
                createNotificationCommand(
                        NotificationType.WORK_CHANGE_OWNER_APPROVED,
                        "교대/대타 요청이 승인되었습니다.",
                        "사장님이 교대/대타 요청을 최종 승인했습니다.",
                        request
                )
        );
    }

    /**
     * 사장 최종 거절 사실을 요청자와 대상 근무자에게 알린다.
     */
    private void notifyWorkersOwnerRejected(WorkChangeRequest request) {
        notificationCommandService.sendToMembers(
                List.of(request.getRequesterMemberId(), request.getTargetMemberId()),
                createNotificationCommand(
                        NotificationType.WORK_CHANGE_OWNER_REJECTED,
                        "교대/대타 요청이 거절되었습니다.",
                        "사장님이 교대/대타 요청을 최종 거절했습니다.",
                        request
                )
        );
    }

    /**
     * 교대/대타 알림 공통 명령을 생성한다.
     */
    private NotificationSendCommand createNotificationCommand(
            NotificationType notificationType,
            String title,
            String body,
            WorkChangeRequest request
    ) {
        return new NotificationSendCommand(
                notificationType,
                PushPolicy.PUSH,
                title,
                body,
                Map.of(
                        "workChangeRequestId", String.valueOf(request.getId()),
                        "workPlaceId", String.valueOf(request.getWorkPlaceId()),
                        "requestType", request.getRequestType().name(),
                        "status", request.getStatus().name()
                )
        );
    }
}
