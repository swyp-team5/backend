package com.autoschedule.member.controller;

import com.autoschedule.auth.jwt.JwtAuthenticationPrincipal;
import com.autoschedule.member.service.MemberWithdrawalService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 인증된 회원 본인의 계정 상태 변경 API를 제공한다.
 */
@RestController
@RequiredArgsConstructor
public class MemberController {

    private final MemberWithdrawalService memberWithdrawalService;

    /**
     * 현재 로그인한 회원의 탈퇴를 신청하고 30일 유예 상태로 전환한다.
     */
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @DeleteMapping("/api/members/me")
    public void requestWithdrawal(@AuthenticationPrincipal JwtAuthenticationPrincipal principal) {
        memberWithdrawalService.requestWithdrawal(principal.memberId());
    }

    /**
     * 현재 로그인한 회원의 탈퇴 신청을 유예 기간 안에서 취소한다.
     */
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PostMapping("/api/members/me/withdrawal-cancel")
    public void cancelWithdrawal(@AuthenticationPrincipal JwtAuthenticationPrincipal principal) {
        memberWithdrawalService.cancelWithdrawal(principal.memberId());
    }
}
