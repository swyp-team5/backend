package com.autoschedule.crew.service;

import com.autoschedule.crew.domain.Crew;
import com.autoschedule.crew.domain.CrewJoinStatus;
import com.autoschedule.crew.domain.CrewRole;
import com.autoschedule.crew.domain.CrewStatus;
import com.autoschedule.crew.dto.CrewListResponse;
import com.autoschedule.crew.dto.OwnerCrewMemberResponse;
import com.autoschedule.crew.dto.WorkerCrewMemberResponse;
import com.autoschedule.crew.repository.CrewRepository;
import com.autoschedule.global.exception.ApiException;
import com.autoschedule.global.exception.ErrorCode;
import com.autoschedule.member.domain.Member;
import com.autoschedule.member.domain.MemberRole;
import com.autoschedule.member.domain.ProfileImage;
import com.autoschedule.member.domain.ProfileImageStatus;
import com.autoschedule.member.repository.MemberRepository;
import com.autoschedule.member.repository.ProfileImageRepository;
import com.autoschedule.workplace.domain.WorkPlace;
import com.autoschedule.workplace.repository.WorkPlaceRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 사업장 근무자 목록 조회와 근무자 삭제 유스케이스를 처리한다.
 */
@Service
@RequiredArgsConstructor
public class CrewManagementService {

    private final MemberRepository memberRepository;
    private final WorkPlaceRepository workPlaceRepository;
    private final CrewRepository crewRepository;
    private final ProfileImageRepository profileImageRepository;

    /**
     * 로그인 회원의 사업장 권한에 따라 근무자 목록을 다른 정보 범위로 조회한다.
     */
    @Transactional(readOnly = true)
    public CrewListResponse getWorkerCrews(Long requesterMemberId, Long workPlaceId) {
        Member requester = findActiveMember(requesterMemberId);
        WorkPlace workPlace = findActiveWorkPlace(workPlaceId);
        if (requester.getRole() == MemberRole.OWNER) {
            validateOwnerWorkPlace(requester.getId(), workPlace);
            List<Crew> workerCrews = findWorkerCrews(workPlace.getId());
            Map<Long, String> profileImageUrls = findProfileImageUrls(workerCrews);
            List<OwnerCrewMemberResponse> crews = workerCrews.stream()
                    .map(crew -> OwnerCrewMemberResponse.from(crew, profileImageUrls.get(crew.getMember().getId())))
                    .toList();
            return new CrewListResponse(crews);
        }

        validateWorkerBelongsToWorkPlace(requester.getId(), workPlace.getId());
        List<Crew> workerCrews = findWorkerCrews(workPlace.getId());
        Map<Long, String> profileImageUrls = findProfileImageUrls(workerCrews);
        List<WorkerCrewMemberResponse> crews = workerCrews.stream()
                .map(crew -> WorkerCrewMemberResponse.from(crew, profileImageUrls.get(crew.getMember().getId())))
                .toList();
        return new CrewListResponse(crews);
    }

    /**
     * 사장님이 본인 사업장의 근무자 크루를 비활성 처리한다.
     */
    @Transactional
    public void deleteWorkerCrew(Long ownerMemberId, Long workPlaceId, Long crewId) {
        Member owner = findActiveMember(ownerMemberId);
        validateOwnerRole(owner);
        WorkPlace workPlace = findActiveWorkPlace(workPlaceId);
        validateOwnerWorkPlace(owner.getId(), workPlace);

        Crew crew = crewRepository.findByIdAndWorkPlace_Id(crewId, workPlace.getId())
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "삭제할 근무자 소속을 찾을 수 없습니다."));
        if (crew.getCrewRole() != CrewRole.WORKER) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "근무자 크루만 삭제할 수 있습니다.");
        }
        if (crew.getJoinStatus() != CrewJoinStatus.APPROVED || crew.getStatus() != CrewStatus.ACTIVE
                || crew.getDeletedAt() != null) {
            throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "삭제할 활성 근무자 소속을 찾을 수 없습니다.");
        }

        crew.deactivate(LocalDateTime.now());
    }

    /**
     * 활성 회원을 조회한다.
     */
    private Member findActiveMember(Long memberId) {
        return memberRepository.findActiveById(memberId)
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED, "인증 정보가 올바르지 않습니다."));
    }

    /**
     * 활성 사업장을 조회한다.
     */
    private WorkPlace findActiveWorkPlace(Long workPlaceId) {
        return workPlaceRepository.findByIdAndStatusAndDeletedAtIsNull(
                        workPlaceId,
                        com.autoschedule.workplace.domain.WorkPlaceStatus.ACTIVE
                )
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "사업장을 찾을 수 없습니다."));
    }

    /**
     * 사장님 계정인지 검증한다.
     */
    private void validateOwnerRole(Member member) {
        if (member.getRole() != MemberRole.OWNER) {
            throw new ApiException(ErrorCode.FORBIDDEN, "사장님만 근무자를 삭제할 수 있습니다.");
        }
    }

    /**
     * 사업장이 요청 사장님의 소유인지 검증한다.
     */
    private void validateOwnerWorkPlace(Long ownerMemberId, WorkPlace workPlace) {
        if (!workPlace.getOwnerMemberId().equals(ownerMemberId)) {
            throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "조회할 수 있는 사업장을 찾을 수 없습니다.");
        }
    }

    /**
     * 근무자가 해당 사업장에 승인된 활성 크루로 소속되어 있는지 검증한다.
     */
    private void validateWorkerBelongsToWorkPlace(Long memberId, Long workPlaceId) {
        boolean exists = crewRepository.existsByMember_IdAndWorkPlace_IdAndJoinStatusAndStatus(
                memberId,
                workPlaceId,
                CrewJoinStatus.APPROVED,
                CrewStatus.ACTIVE
        );
        if (!exists) {
            throw new ApiException(ErrorCode.FORBIDDEN, "해당 사업장에 접근 권한이 없습니다.");
        }
    }

    /**
     * 사업장의 승인된 활성 근무자 크루를 회원 정보와 함께 조회한다.
     */
    private List<Crew> findWorkerCrews(Long workPlaceId) {
        return crewRepository.findActiveApprovedWorkersWithMember(
                workPlaceId,
                CrewJoinStatus.APPROVED,
                CrewStatus.ACTIVE
        );
    }

    /**
     * 크루 목록의 회원 ID로 활성 프로필 이미지를 한 번에 조회해 URL Map으로 변환한다.
     */
    private Map<Long, String> findProfileImageUrls(List<Crew> crews) {
        List<Long> memberIds = crews.stream()
                .map(crew -> crew.getMember().getId())
                .toList();
        if (memberIds.isEmpty()) {
            return Map.of();
        }
        return profileImageRepository.findByMember_IdInAndStatusAndDeletedAtIsNull(
                        memberIds,
                        ProfileImageStatus.ACTIVE
                )
                .stream()
                .collect(Collectors.toMap(
                        profileImage -> profileImage.getMember().getId(),
                        ProfileImage::getImageUrl,
                        (first, second) -> first
                ));
    }
}
