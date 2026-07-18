package com.autoschedule.auth.dto;

import com.autoschedule.member.domain.Member;
import com.autoschedule.member.domain.MemberRole;
import com.autoschedule.member.domain.MemberStatus;

/**
 * 로그인 성공 응답에 포함할 최소 회원 정보를 표현한다.
 */
public record MemberSummaryResponse(
        Long memberId,
        String name,
        MemberRole role,
        MemberStatus status
) {

    /**
     * 회원 엔티티를 모바일 응답용 요약 정보로 변환한다.
     */
    public static MemberSummaryResponse from(Member member) {
        return new MemberSummaryResponse(member.getId(), member.getName(), member.getRole(), member.getStatus());
    }
}
