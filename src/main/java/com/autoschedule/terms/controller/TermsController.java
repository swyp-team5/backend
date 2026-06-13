package com.autoschedule.terms.controller;

import com.autoschedule.member.domain.MemberRole;
import com.autoschedule.terms.dto.TermsSignupResponse;
import com.autoschedule.terms.service.TermsQueryService;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 회원가입과 서비스 이용에 필요한 약관 조회 API를 제공한다.
 */
@Validated
@RestController
@RequestMapping("/api/terms")
@RequiredArgsConstructor
public class TermsController {

    private final TermsQueryService termsQueryService;

    /**
     * 회원가입 역할에 필요한 활성 약관 목록을 조회한다.
     */
    @GetMapping("/signup")
    public ResponseEntity<TermsSignupResponse> getSignupTerms(
            @RequestParam(value = "role", required = false)
            @NotNull(message = "회원가입 역할은 필수입니다.")
            MemberRole role
    ) {
        return ResponseEntity.ok(termsQueryService.getSignupTerms(role));
    }
}
