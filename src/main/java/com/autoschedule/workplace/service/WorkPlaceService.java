package com.autoschedule.workplace.service;

import com.autoschedule.crew.domain.Crew;
import com.autoschedule.crew.domain.CrewJoinStatus;
import com.autoschedule.crew.domain.CrewStatus;
import com.autoschedule.crew.repository.CrewRepository;
import com.autoschedule.global.exception.ApiException;
import com.autoschedule.global.exception.ErrorCode;
import com.autoschedule.member.repository.MemberRepository;
import com.autoschedule.workplace.domain.WorkPlaceStatus;
import com.autoschedule.workplace.dto.MyWorkPlaceListResponse;
import com.autoschedule.workplace.dto.MyWorkPlaceResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 홈 화면에서 사용하는 사업장 조회 유스케이스를 처리한다.
 */
@Service
@RequiredArgsConstructor
public class WorkPlaceService {

    private final MemberRepository memberRepository;
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
     * 인증 주체가 활성 회원인지 확인한다.
     */
    private void validateActiveMember(Long memberId) {
        memberRepository.findActiveById(memberId)
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED, "인증 정보가 올바르지 않습니다."));
    }
}
