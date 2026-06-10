package com.autoschedule.global.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * API 에러 응답에 사용하는 서비스 공통 에러 코드를 정의한다.
 */
@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    VALIDATION_FAILED("4000", "요청 값이 올바르지 않습니다."),
    INVALID_REQUEST("4001", "잘못된 요청입니다."),
    UNAUTHORIZED("4002", "인증이 필요합니다."),
    FORBIDDEN("4003", "접근 권한이 없습니다."),
    RESOURCE_NOT_FOUND("4004", "요청한 리소스를 찾을 수 없습니다."),
    CONFLICT("4005", "요청이 현재 상태와 충돌합니다."),
    INTERNAL_SERVER_ERROR("500", "서버 내부 오류가 발생했습니다.");

    private final String code;
    private final String message;
}
