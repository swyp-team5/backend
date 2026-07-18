package com.autoschedule.member.controller;

import com.autoschedule.auth.jwt.JwtAuthenticationPrincipal;
import com.autoschedule.member.dto.MemberProfileResponse;
import com.autoschedule.member.dto.MemberProfileUpdateRequest;
import com.autoschedule.member.dto.ProfileImageConfirmRequest;
import com.autoschedule.member.dto.ProfileImageUploadUrlRequest;
import com.autoschedule.member.dto.ProfileImageUploadUrlResponse;
import com.autoschedule.member.service.MemberProfileService;
import com.autoschedule.member.service.MemberWithdrawalService;
import com.autoschedule.member.service.ProfileImageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 로그인 회원 본인의 계정, 프로필, 탈퇴 API를 제공한다.
 */
@RestController
@RequiredArgsConstructor
public class MemberController {

    private final MemberWithdrawalService memberWithdrawalService;
    private final MemberProfileService memberProfileService;
    private final ProfileImageService profileImageService;

    /**
     * 현재 로그인한 회원의 프로필 기본 정보와 프로필 이미지 정보를 조회한다.
     */
    @GetMapping("/api/members/me/profile")
    public MemberProfileResponse getProfile(@AuthenticationPrincipal JwtAuthenticationPrincipal principal) {
        return memberProfileService.getProfile(principal.memberId());
    }

    /**
     * 현재 로그인한 회원의 이름과 휴대폰 번호를 수정한다.
     */
    @PatchMapping("/api/members/me/profile")
    public MemberProfileResponse updateProfile(
            @AuthenticationPrincipal JwtAuthenticationPrincipal principal,
            @Valid @RequestBody MemberProfileUpdateRequest request
    ) {
        return memberProfileService.updateProfile(principal.memberId(), request);
    }

    /**
     * 현재 로그인한 회원의 프로필 이미지 S3 직접 업로드 URL을 발급한다.
     */
    @PostMapping("/api/members/me/profile-image/upload-url")
    public ProfileImageUploadUrlResponse createProfileImageUploadUrl(
            @AuthenticationPrincipal JwtAuthenticationPrincipal principal,
            @Valid @RequestBody ProfileImageUploadUrlRequest request
    ) {
        return profileImageService.createUploadUrl(principal.memberId(), request);
    }

    /**
     * S3 업로드가 완료된 객체를 검증하고 현재 회원의 프로필 이미지로 확정한다.
     */
    @PutMapping("/api/members/me/profile-image")
    public MemberProfileResponse confirmProfileImage(
            @AuthenticationPrincipal JwtAuthenticationPrincipal principal,
            @Valid @RequestBody ProfileImageConfirmRequest request
    ) {
        return profileImageService.confirmUploadedImage(principal.memberId(), request);
    }

    /**
     * 현재 로그인한 회원의 프로필 이미지를 삭제한다.
     */
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @DeleteMapping("/api/members/me/profile-image")
    public void deleteProfileImage(@AuthenticationPrincipal JwtAuthenticationPrincipal principal) {
        profileImageService.deleteProfileImage(principal.memberId());
    }

    /**
     * 현재 로그인한 회원의 탈퇴를 요청하고 30일 유예 상태로 전환한다.
     */
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @DeleteMapping("/api/members/me")
    public void requestWithdrawal(@AuthenticationPrincipal JwtAuthenticationPrincipal principal) {
        memberWithdrawalService.requestWithdrawal(principal.memberId());
    }

    /**
     * 현재 로그인한 회원의 탈퇴 요청을 유예 기간 안에서 취소한다.
     */
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PostMapping("/api/members/me/withdrawal-cancel")
    public void cancelWithdrawal(@AuthenticationPrincipal JwtAuthenticationPrincipal principal) {
        memberWithdrawalService.cancelWithdrawal(principal.memberId());
    }
}
