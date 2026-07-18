package com.autoschedule.global.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * 서비스에서 의도적으로 발생시키는 API 예외를 표현한다.
 */
@Getter
public class ApiException extends RuntimeException {

    private final ErrorCode errorCode;
    private final HttpStatus httpStatus;

    /**
     * 에러 코드의 기본 HTTP 상태로 API 예외를 생성한다.
     */
    public ApiException(ErrorCode errorCode) {
        this(errorCode, defaultStatus(errorCode), errorCode.getMessage());
    }

    /**
     * 에러 코드의 기본 HTTP 상태와 별도 응답 메시지로 API 예외를 생성한다.
     */
    public ApiException(ErrorCode errorCode, String message) {
        this(errorCode, defaultStatus(errorCode), message);
    }

    /**
     * 에러 코드와 명시한 HTTP 상태로 API 예외를 생성한다.
     */
    public ApiException(ErrorCode errorCode, HttpStatus httpStatus) {
        this(errorCode, httpStatus, errorCode.getMessage());
    }

    /**
     * 에러 코드, HTTP 상태, 응답 메시지를 모두 명시해 API 예외를 생성한다.
     */
    public ApiException(ErrorCode errorCode, HttpStatus httpStatus, String message) {
        super(message);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
    }

    /**
     * 공통 에러 코드에 대응하는 기본 HTTP 상태를 결정한다.
     */
    private static HttpStatus defaultStatus(ErrorCode errorCode) {
        return switch (errorCode) {
            case VALIDATION_FAILED, INVALID_REQUEST -> HttpStatus.BAD_REQUEST;
            case UNAUTHORIZED -> HttpStatus.UNAUTHORIZED;
            case FORBIDDEN -> HttpStatus.FORBIDDEN;
            case RESOURCE_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case CONFLICT -> HttpStatus.CONFLICT;
            case INTERNAL_SERVER_ERROR -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }

}
