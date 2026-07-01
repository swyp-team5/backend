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
import com.autoschedule.schedulecondition.domain.TimeDetail;
import com.autoschedule.schedulecondition.domain.TimeDetailStatus;
import com.autoschedule.schedulecondition.domain.WeekSchedule;
import com.autoschedule.schedulecondition.domain.WeekScheduleStatus;
import com.autoschedule.schedulecondition.repository.TimeDetailRepository;
import com.autoschedule.schedulecondition.repository.WeekScheduleRepository;
import com.autoschedule.workerselect.domain.WorkerSelectSubmission;
import com.autoschedule.workerselect.domain.WorkerSelectSubmissionStatus;
import com.autoschedule.workerselect.domain.WorkerUnavailableTimeDetail;
import com.autoschedule.workerselect.dto.WorkerSelectMemberStatusResponse;
import com.autoschedule.workerselect.dto.WorkerSelectRequest;
import com.autoschedule.workerselect.dto.WorkerSelectResponse;
import com.autoschedule.workerselect.dto.WorkerSelectStatusResponse;
import com.autoschedule.workerselect.dto.WorkerSelectTimeDetailResponse;
import com.autoschedule.workerselect.repository.WorkerSelectSubmissionRepository;
import com.autoschedule.workerselect.repository.WorkerUnavailableTimeDetailRepository;
import com.autoschedule.workplace.domain.WorkPlace;
import com.autoschedule.workplace.domain.WorkPlaceStatus;
import com.autoschedule.workplace.repository.WorkPlaceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

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

    /**
     * 근무자가 선택한 불가능 근무 타임을 저장한다.
     */
    @Transactional
    public WorkerSelectResponse selectWorkerUnavailable(
            Long memberId,
            Long workPlaceId,
            WorkerSelectRequest request
    ) {
        // 1. 회원 및 사업장 검증
        Member member = findActiveMember(memberId);
        WorkPlace workPlace = findActiveWorkPlace(workPlaceId);
        validateCrewMember(workPlace.getId(), member.getId());

        // 2. 중복 제출 검증
        validateAlreadySubmitted(member.getId(), workPlace.getId(), request.weekScheduleId());

        List<Long> timeDetailIds = request.timeDetails() == null ? List.of() : request.timeDetails(); // null 이면 빈 리스트로 정규화
        List<Long> uniqueTimeDetailIds = timeDetailIds.stream()
                .distinct()
                .toList();

        // [변경] 빈 리스트 분기 제거 — submission을 항상 먼저 저장하고, timeDetail이 있을 때만 추가 저장
        // 3. 제출 현황(submission) 저장 — 빈 리스트든 아니든 항상 단건 저장
        WorkerSelectSubmission submission;
        try {
            submission = workerSelectSubmissionRepository.save(
                    WorkerSelectSubmission.create(workPlace.getId(), request.weekScheduleId(), member.getId())
            );
        } catch (DataIntegrityViolationException e) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "이미 근무 불가 정보를 제출했습니다.");
        }

        // 4. 빈 리스트면 submission만 저장하고 종료 (time_detail 레코드 생성 없음)
        if (uniqueTimeDetailIds.isEmpty()) {
            return WorkerSelectResponse.of(workPlace.getId(), member.getId(), List.of());
        }

        // 5. timeDetailId 유효성 검증 — 해당 사업장 + 주간 스케줄에 속한 값인지 확인
        List<TimeDetail> timeDetails = timeDetailRepository
                .findAllByIdInAndDay_WeekSchedule_IdAndDay_WeekSchedule_WorkPlace_IdAndStatusAndDeletedAtIsNull(
                        uniqueTimeDetailIds,
                        request.weekScheduleId(),
                        workPlace.getId(),
                        TimeDetailStatus.ACTIVE
                );

        if (timeDetails.size() != uniqueTimeDetailIds.size()) {
            throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "조회할 수 있는 근무 타임 정보를 찾을 수 없습니다.");
        }

        // [변경] WorkerUnavailable 대신 WorkerUnavailableTimeDetail로 저장
        // 6. 불가 타임 목록 저장
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
     * 사업장 근무자들의 근무 불가 제출 여부를 조회한다.
     */
    @Transactional(readOnly = true)
    public WorkerSelectStatusResponse getWorkerSelectStatus(
            Long ownerMemberId,
            Long workPlaceId,
            Long weekScheduleId
    ) {
        // 1. 사장 및 사업장 검증
        Member owner = findActiveMember(ownerMemberId);
        WorkPlace workPlace = findOwnedActiveWorkPlace(workPlaceId, owner.getId());
        WeekSchedule weekSchedule = findActiveWeekSchedule(weekScheduleId, workPlace.getId());

        // 2. 사업장의 근무자 크루 목록 조회
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
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "해당 주차의 스케줄 조건을 찾을 수 없습니다."));
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
            throw new ApiException(ErrorCode.FORBIDDEN, "이 회원은 해당 사업장의 크루원이 아닙니다.");
        }
    }

    private WorkPlace findActiveWorkPlace(Long workPlaceId) {
        return workPlaceRepository
                .findByIdAndStatusAndDeletedAtIsNull(workPlaceId, WorkPlaceStatus.ACTIVE)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "사업장을 찾을 수 없습니다."));
    }
}