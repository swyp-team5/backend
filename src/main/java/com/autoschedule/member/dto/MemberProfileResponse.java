package com.autoschedule.member.dto;

import com.autoschedule.member.domain.Member;
import com.autoschedule.member.domain.ProfileImage;

/**
 * 회원 본인의 프로필 조회/수정 응답 정보를 표현한다.
 */
public record MemberProfileResponse(
        Long memberId,
        String name,
        String phoneNumber,
        MemberProfileImageResponse profileImage
) {

    /**
     * 회원과 현재 프로필 이미지를 모바일 클라이언트 응답 DTO로 변환한다.
     */
    public static MemberProfileResponse from(Member member, ProfileImage profileImage) {
        return new MemberProfileResponse(
                member.getId(),
                member.getName(),
                member.getPhoneNumber(),
                profileImage == null ? null : MemberProfileImageResponse.from(profileImage)
        );
    }
}
