package com.autoschedule.workplace.service;

import com.autoschedule.crew.domain.Crew;
import com.autoschedule.crew.domain.CrewJoinStatus;
import com.autoschedule.crew.domain.CrewRole;
import com.autoschedule.crew.domain.CrewStatus;
import com.autoschedule.crew.repository.CrewRepository;
import com.autoschedule.global.exception.ApiException;
import com.autoschedule.global.exception.ErrorCode;
import com.autoschedule.member.domain.Member;
import com.autoschedule.member.domain.MemberRole;
import com.autoschedule.member.repository.MemberRepository;
import com.autoschedule.workplace.domain.WorkPlace;
import com.autoschedule.workplace.domain.WorkPlaceStatus;
import com.autoschedule.workplace.dto.MyWorkPlaceListResponse;
import com.autoschedule.workplace.dto.MyWorkPlaceResponse;
import com.autoschedule.workplace.dto.WorkPlaceCreateRequest;
import com.autoschedule.workplace.dto.WorkPlacePhoneNumberUpdateRequest;
import com.autoschedule.workplace.repository.WorkPlaceRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 사업장 조회, 생성, 부가 정보 수정 유스케이스를 처리한다.
 */
@Service
@RequiredArgsConstructor
public class WorkPlaceService {

    private final MemberRepository memberRepository;
    private final WorkPlaceRepository workPlaceRepository;
    private final CrewRepository crewRepository;

    /**
     * 현재 로그인한 회원이 선택할 수 있는 승인된 활성 사업장 목록을 조회한다.
     */
    @Transactional(readOnly = true)
    public MyWorkPlaceListResponse getMyWorkPlaces(Long memberId) {
        validateActiveMember(memberId);
        List<Crew> crews = crewRepository.findMyActiveApprovedWorkPlaces(
                memberId,
                CrewJoinStatus.APPROVED,
                CrewStatus.ACTIVE,
                WorkPlaceStatus.ACTIVE
        );
        List<MyWorkPlaceResponse> workPlaces = crews.stream()
                .map(MyWorkPlaceResponse::from)
                .toList();
        return new MyWorkPlaceListResponse(workPlaces);
    }

    /**
     * 로그인한 사장님이 추가 사업장을 생성하고 본인을 OWNER 크루로 등록한다.
     */
    @Transactional
    public MyWorkPlaceResponse createAdditionalWorkPlace(Long ownerMemberId, WorkPlaceCreateRequest request) {
        Member owner = findActiveOwner(ownerMemberId);
        return createOwnerWorkPlace(owner, new WorkPlaceCreateCommand(
                request.size(),
                request.name(),
                request.roadAddress(),
                request.detailAddress(),
                request.phoneNumber()
        ));
    }

    /**
     * 사장님 회원가입 트랜잭션 안에서 최초 사업장을 생성하고 OWNER 크루를 등록한다.
     */
    public void createInitialWorkPlaceForSignup(Member owner, WorkPlaceCreateCommand command) {
        validateOwnerRole(owner);
        createOwnerWorkPlace(owner, command);
    }

    /**
     * 사장님이 본인 소유 사업장의 전화번호 부가 정보를 추가, 수정 또는 삭제한다.
     */
    @Transactional
    public MyWorkPlaceResponse updatePhoneNumber(
            Long ownerMemberId,
            Long workPlaceId,
            WorkPlacePhoneNumberUpdateRequest request
    ) {
        Member owner = findActiveOwner(ownerMemberId);
        WorkPlace workPlace = findOwnedActiveWorkPlace(workPlaceId, owner.getId());
        workPlace.updatePhoneNumber(request.phoneNumber());
        Crew ownerCrew = findOwnerCrew(owner.getId(), workPlace.getId());
        return MyWorkPlaceResponse.from(ownerCrew);
    }

    /**
     * 사업장과 사장님 OWNER 크루 소속을 같은 트랜잭션 안에서 생성한다.
     */
    private MyWorkPlaceResponse createOwnerWorkPlace(Member owner, WorkPlaceCreateCommand command) {
        WorkPlace workPlace = workPlaceRepository.save(WorkPlace.create(
                owner.getId(),
                command.size(),
                command.name(),
                command.roadAddress(),
                command.detailAddress(),
                command.phoneNumber()
        ));
        Crew crew = crewRepository.save(Crew.createOwner(owner, workPlace));
        return MyWorkPlaceResponse.from(crew);
    }

    /**
     * 인증 주체가 활성 회원인지 확인한다.
     */
    private void validateActiveMember(Long memberId) {
        memberRepository.findActiveById(memberId)
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED, "인증 정보가 올바르지 않습니다."));
    }

    /**
     * 활성 사장님 회원을 조회하고 아니면 권한 오류를 반환한다.
     */
    private Member findActiveOwner(Long memberId) {
        Member member = memberRepository.findActiveById(memberId)
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED, "인증 정보가 올바르지 않습니다."));
        validateOwnerRole(member);
        return member;
    }

    /**
     * 사업장 생성 주체가 사장님 계정인지 검증한다.
     */
    private void validateOwnerRole(Member member) {
        if (member.getRole() != MemberRole.OWNER) {
            throw new ApiException(ErrorCode.FORBIDDEN, "사장님 계정만 사업장을 생성할 수 있습니다.");
        }
    }

    /**
     * 사장님이 소유한 활성 사업장을 조회한다.
     */
    private WorkPlace findOwnedActiveWorkPlace(Long workPlaceId, Long ownerMemberId) {
        return workPlaceRepository.findOwnedActiveById(workPlaceId, ownerMemberId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "사업장을 찾을 수 없습니다."));
    }

    /**
     * 사장님의 활성 OWNER 크루 소속을 조회한다.
     */
    private Crew findOwnerCrew(Long ownerMemberId, Long workPlaceId) {
        return crewRepository
                .findByMember_IdAndWorkPlace_IdAndJoinStatusAndCrewRoleAndStatus(
                        ownerMemberId,
                        workPlaceId,
                        CrewJoinStatus.APPROVED,
                        CrewRole.OWNER,
                        CrewStatus.ACTIVE
                )
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "사업장 소속 정보를 찾을 수 없습니다."));
    }
}
