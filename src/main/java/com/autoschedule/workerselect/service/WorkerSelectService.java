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
import com.autoschedule.schedulecondition.repository.TimeDetailRepository;
import com.autoschedule.workerselect.domain.WorkerUnavailable;
import com.autoschedule.workerselect.domain.WorkerUnavailableStatus;
import com.autoschedule.workerselect.dto.WorkerSelectMemberStatusResponse;
import com.autoschedule.workerselect.dto.WorkerSelectRequest;
import com.autoschedule.workerselect.dto.WorkerSelectResponse;
import com.autoschedule.workerselect.dto.WorkerSelectStatusResponse;
import com.autoschedule.workerselect.dto.WorkerSelectTimeDetailResponse;
import com.autoschedule.workerselect.repository.WorkerUnavailableRepository;
import com.autoschedule.workplace.domain.WorkPlace;
import com.autoschedule.workplace.domain.WorkPlaceStatus;
import com.autoschedule.workplace.repository.WorkPlaceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 근무자 불가능 근무 타임 제출 기능을 처리한다.
 */
@Service
@RequiredArgsConstructor
public class WorkerSelectService {

    private final MemberRepository memberRepository;
    private final WorkPlaceRepository workPlaceRepository;
    private final CrewRepository crewRepository;
    private final TimeDetailRepository timeDetailRepository;
    private final WorkerUnavailableRepository workerUnavailableRepository;

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

        validateCrewMember(
                workPlace.getId(),
                member.getId()
        );

        // 2. 중복 제출 검증
        validateAlreadySubmitted(member.getId()); // 근무자가 이미 불가능한 일자를 제출했는지 검증. -> 이미 한번 제출하면 재 제출은 불가하다.

        List<Long> timeDetailIds = request.timeDetails(); // 중복이 포함된 원본 timeDetails
        List<Long> uniqueTimeDetailIds = timeDetailIds.stream()
                .distinct()
                .toList(); // 중복 제거된 timeDetails

        // 3. 빈 리스트 처리
        if (timeDetailIds.isEmpty()) {
            WorkerUnavailable workerUnavailable = WorkerUnavailable.create(
                    null,
                    member.getId()
            );

            workerUnavailableRepository.save(workerUnavailable);

            return WorkerSelectResponse.of(
                    workPlaceId,
                    member.getId(),
                    List.of()
            );
        }

        // 4. 요청값에서 가져온 중복이 제거된 time_deatil_id값들이 사업장에 존재하는 값인지 조회한다.
        List<TimeDetail> timeDetails = timeDetailRepository
                .findAllByIdInAndDay_WeekSchedule_WorkPlace_IdAndStatusAndDeletedAtIsNull(
                        uniqueTimeDetailIds,
                        workPlaceId,
                        TimeDetailStatus.ACTIVE
                );

        if (timeDetails.size() != uniqueTimeDetailIds.size()) {
            throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "조회할 수 있는 근무 타임 정보를 찾을 수 없습니다.");
        }

        // 5. 중복 저장 방지 후 일괄 저장
        Set<Long> alreadySubmittedIds = new HashSet<>(
                workerUnavailableRepository
                        .findTimeDetailIdsByMemberIdAndTimeDetail_IdInAndStatusAndDeletedAtIsNull(
                                member.getId(),
                                timeDetailIds,
                                WorkerUnavailableStatus.ACTIVE
                        )
        );

        List<WorkerUnavailable> toSave = timeDetails.stream()
                .filter(td -> !alreadySubmittedIds.contains(td.getId()))
                .map(td -> WorkerUnavailable.create(td, member.getId()))
                .toList();

        workerUnavailableRepository.saveAll(toSave);

        // 6. 응답 반환
        List<WorkerSelectTimeDetailResponse> timeDetailResponses = timeDetails.stream()
                .map(WorkerSelectTimeDetailResponse::from)
                .toList();

        return WorkerSelectResponse.of(
                workPlaceId,
                member.getId(),
                timeDetailResponses
        );
    }

    /**
     * 사업장 근무자들의 근무 불가 제출 여부를 조회한다.
     */
    @Transactional(readOnly = true)
    public WorkerSelectStatusResponse getWorkerSelectStatus(
            Long ownerMemberId,
            Long workPlaceId
    ) {
        // 1. 사장 및 사업장 검증
        Member owner = findActiveMember(ownerMemberId); // 현재 로그인한 사장 회원을 조회함
        WorkPlace workPlace = findOwnedActiveWorkPlace(workPlaceId, owner.getId()); // 사업장 존재 + 사장 권한 확인

        // 2. 사업장의 근무자 크루 목록 조회
        List<Crew> crews = crewRepository.findByWorkPlace_IdAndJoinStatusAndCrewRoleAndStatus(
                workPlace.getId(),
                CrewJoinStatus.APPROVED,
                CrewRole.WORKER,
                CrewStatus.ACTIVE
        );

        // 3. 제출 완료한 멤버 ID 목록 조회
        List<Long> crewMemberIds = crews.stream()
                .map(crew -> crew.getMember().getId())
                .toList();

        List<Long> submittedMemberIds = workerUnavailableRepository
                .findByMemberIdInAndStatusAndDeletedAtIsNull(
                        crewMemberIds,
                        WorkerUnavailableStatus.ACTIVE
                )
                .stream()
                .map(WorkerUnavailable::getMemberId)
                .distinct()
                .toList();

        // 4. 제출 여부 포함 응답 생성
        List<WorkerSelectMemberStatusResponse> workers = crews.stream()
                .map(crew -> WorkerSelectMemberStatusResponse.from(
                        crew,
                        submittedMemberIds
                ))
                .toList();

        return WorkerSelectStatusResponse.of(
                workPlace.getId(),
                workers
        );
    }

    /**
     * 이미 근무 불가 정보를 제출했는지 확인한다.
     */
    private void validateAlreadySubmitted(Long memberId) {
        boolean exists = workerUnavailableRepository.existsByMemberIdAndStatusAndDeletedAtIsNull(
                memberId,
                WorkerUnavailableStatus.ACTIVE
        );

        if (exists) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "이미 근무 불가 정보를 제출했습니다.");
        }
    }

    /**
     * 활성화된 멤버인지 검증하는 메서드
     */
    private Member findActiveMember(Long memberId) {
        return memberRepository.findActiveById(memberId)
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED, "인증 정보가 올바르지 않습니다."));
    }

    /**
     * 사업장 존재 여부를 먼저 확인한 뒤 소유자를 검증한다.
     */
    private WorkPlace findOwnedActiveWorkPlace(Long workPlaceId, Long ownerMemberId) {
        WorkPlace workPlace = workPlaceRepository
                .findByIdAndStatusAndDeletedAtIsNull(workPlaceId, WorkPlaceStatus.ACTIVE)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "사업장을 찾을 수 없습니다."));

        if (!workPlace.getOwnerMemberId().equals(ownerMemberId)) {
            throw new ApiException(ErrorCode.FORBIDDEN, "권한이 없습니다.");
        }

        return workPlace;
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
     * 사업장 존재 여부를 먼저 확인한 뒤 소유자를 검증한다.
     */
    private WorkPlace findActiveWorkPlace(Long workPlaceId) {
        WorkPlace workPlace = workPlaceRepository
                .findByIdAndStatusAndDeletedAtIsNull(workPlaceId, WorkPlaceStatus.ACTIVE)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "사업장을 찾을 수 없습니다."));

        return workPlace;
    }
}