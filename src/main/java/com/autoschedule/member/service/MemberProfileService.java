package com.autoschedule.member.service;

import com.autoschedule.global.exception.ApiException;
import com.autoschedule.global.exception.ErrorCode;
import com.autoschedule.member.domain.Member;
import com.autoschedule.member.domain.ProfileImage;
import com.autoschedule.member.domain.ProfileImageStatus;
import com.autoschedule.member.dto.MemberProfileResponse;
import com.autoschedule.member.dto.MemberProfileUpdateRequest;
import com.autoschedule.member.repository.MemberRepository;
import com.autoschedule.member.repository.ProfileImageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회원 본인의 프로필 조회와 기본 정보 수정을 처리한다.
 */
@Service
@RequiredArgsConstructor
public class MemberProfileService {

    private final MemberRepository memberRepository;
    private final ProfileImageRepository profileImageRepository;

    /**
     * 현재 로그인한 회원의 프로필 정보를 조회한다.
     */
    @Transactional(readOnly = true)
    public MemberProfileResponse getProfile(Long memberId) {
        Member member = findActiveMember(memberId);
        ProfileImage profileImage = findActiveProfileImage(member.getId());
        return MemberProfileResponse.from(member, profileImage);
    }

    /**
     * 현재 로그인한 회원의 이름과 휴대폰 번호를 수정한다.
     */
    @Transactional
    public MemberProfileResponse updateProfile(Long memberId, MemberProfileUpdateRequest request) {
        Member member = findActiveMember(memberId);
        member.updateProfile(request.name(), request.phoneNumber());
        ProfileImage profileImage = findActiveProfileImage(member.getId());
        return MemberProfileResponse.from(member, profileImage);
    }

    /**
     * 인증된 회원 ID로 활성 회원을 조회한다.
     */
    private Member findActiveMember(Long memberId) {
        return memberRepository.findActiveById(memberId)
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED, "인증 정보가 올바르지 않습니다."));
    }

    /**
     * 회원의 현재 활성 프로필 이미지를 조회한다.
     */
    private ProfileImage findActiveProfileImage(Long memberId) {
        return profileImageRepository.findByMember_IdAndStatusAndDeletedAtIsNull(memberId, ProfileImageStatus.ACTIVE)
                .orElse(null);
    }
}
