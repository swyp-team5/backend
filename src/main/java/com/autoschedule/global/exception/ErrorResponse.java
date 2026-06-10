package com.autoschedule.global.exception;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 모든 API 예외 응답에서 사용하는 표준 에러 응답 본문이다.
 */
public record ErrorResponse(
        String code,
        String message,
        List<FieldError> errors,
        String path,
        LocalDateTime timestamp
) {

    /**
     * 필드 오류가 없는 단일 에러 응답을 생성한다.
     */
    public static ErrorResponse of(ErrorCode errorCode, String message, String path) {
        return new ErrorResponse(errorCode.getCode(), message, List.of(), path, LocalDateTime.now());
    }

    /**
     * 필드별 검증 오류를 포함한 에러 응답을 생성한다.
     */
    public static ErrorResponse of(ErrorCode errorCode, String message, List<FieldError> errors, String path) {
        return new ErrorResponse(errorCode.getCode(), message, errors, path, LocalDateTime.now());
    }

    /**
     * 요청 필드 단위의 검증 오류 정보를 표현한다.
     */
    public record FieldError(
            String field,
            String message
    ) {
    }
}
