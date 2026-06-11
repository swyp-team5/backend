package com.autoschedule.auth.controller;

import com.autoschedule.auth.dto.AuthResponse;
import com.autoschedule.auth.dto.OwnerSignupRequest;
import com.autoschedule.auth.dto.RefreshTokenRequest;
import com.autoschedule.auth.dto.SocialLoginRequest;
import com.autoschedule.auth.dto.WorkerSignupRequest;
import com.autoschedule.auth.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 소셜 로그인, 사장님 회원가입, 근무자 회원가입, 토큰 재발급 API를 제공한다.
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * 소셜 인증 정보를 검증하고 기존 회원이면 로그인, 신규 사용자이면 회원가입 필요 상태를 반환한다.
     */
    @PostMapping("/social-login")
    public ResponseEntity<AuthResponse> socialLogin(@Valid @RequestBody SocialLoginRequest request) {
        return ResponseEntity.ok(authService.socialLogin(request));
    }

    /**
     * 사장님 회원가입을 완료하고 즉시 로그인 토큰을 발급한다.
     */
    @PostMapping("/signup/owner")
    public ResponseEntity<AuthResponse> signupOwner(@Valid @RequestBody OwnerSignupRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.signupOwner(request));
    }

    /**
     * 근무자 회원가입을 완료하고 즉시 로그인 토큰을 발급한다.
     */
    @PostMapping("/signup/worker")
    public ResponseEntity<AuthResponse> signupWorker(@Valid @RequestBody WorkerSignupRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.signupWorker(request));
    }

    /**
     * 기기별 refresh token을 검증하고 새 access token과 refresh token을 발급한다.
     */
    @PostMapping("/token/refresh")
    public ResponseEntity<AuthResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return ResponseEntity.ok(authService.refresh(request));
    }

    /**
     * 현재 기기의 refresh token 세션을 삭제해 로그아웃 처리한다.
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody RefreshTokenRequest request) {
        authService.logout(request);
        return ResponseEntity.noContent().build();
    }
}
