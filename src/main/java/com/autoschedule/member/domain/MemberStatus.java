package com.autoschedule.member.domain;

/**
 * 회원 계정의 사용 가능 상태를 정의한다.
 */
public enum MemberStatus {
    ACTIVE,                 // 활성화
    DELETE                  // 탈퇴 완료 회원 (유예 기간 없이 즉시 전환)

    // 유예기간 정책 롤백 대비 보존 — 더 이상 사용하지 않음
    // WITHDRAWAL_PENDING,     // 회원 탈퇴 신청 상태(30일)
    // WITHDRAWN,              // 탈퇴 완료 회원 (30일)
}
