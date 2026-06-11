package com.autoschedule.member.domain;

/**
 * 회원 계정의 사용 가능 상태를 정의한다.
 */
public enum MemberStatus {
    ACTIVE,                 // 활성화
    WITHDRAWAL_PENDING,     // 회원 탈퇴 신청 상태(30일)
    WITHDRAWN               // 탈퇴 완료 회원
}
