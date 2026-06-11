package com.autoschedule.global.exception;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.util.StringUtils;
import org.springframework.validation.FieldError;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

/**
 * 컨트롤러 계층에서 발생한 예외를 표준 에러 응답으로 변환한다.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final String DEFAULT_FIELD_ERROR_MESSAGE = "올바르지 않은 값입니다.";

    /**
     * 서비스에서 의도적으로 던진 API 예외를 지정된 HTTP 상태와 에러 코드로 응답한다.
     */
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponse> handleApiException(ApiException exception, HttpServletRequest request) {
        ErrorCode errorCode = exception.getErrorCode();
        ErrorResponse response = ErrorResponse.of(errorCode, exception.getMessage(), request.getRequestURI());
        return ResponseEntity.status(exception.getHttpStatus()).body(response);
    }

    /**
     * 요청 본문 DTO 검증 실패를 필드별 오류 목록이 포함된 400 응답으로 변환한다.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentNotValid(
            MethodArgumentNotValidException exception,
            HttpServletRequest request
    ) {
        List<ErrorResponse.FieldError> fieldErrors = exception.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(this::toFieldError)
                .toList();

        ErrorCode errorCode = ErrorCode.VALIDATION_FAILED;
        ErrorResponse response = ErrorResponse.of(errorCode, errorCode.getMessage(), fieldErrors, request.getRequestURI());
        return ResponseEntity.badRequest().body(response);
    }

    /**
     * 컨트롤러 메서드 파라미터 검증 실패를 필드별 오류 목록이 포함된 400 응답으로 변환한다.
     */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ErrorResponse> handleHandlerMethodValidation(
            HandlerMethodValidationException exception,
            HttpServletRequest request
    ) {
        List<ErrorResponse.FieldError> fieldErrors = exception.getParameterValidationResults()
                .stream()
                .flatMap(result -> result.getResolvableErrors()
                        .stream()
                        .map(error -> new ErrorResponse.FieldError(
                                resolveParameterName(result),
                                resolveErrorMessage(error)
                        )))
                .toList();

        ErrorCode errorCode = ErrorCode.VALIDATION_FAILED;
        ErrorResponse response = ErrorResponse.of(errorCode, errorCode.getMessage(), fieldErrors, request.getRequestURI());
        return ResponseEntity.badRequest().body(response);
    }

    /**
     * 처리되지 않은 예외를 로깅하고 내부 서버 오류 응답으로 변환한다.
     */
    /**
     * 메서드 기반 권한 검증 실패를 403 응답으로 변환한다.
     */
    @ExceptionHandler({AccessDeniedException.class, AuthorizationDeniedException.class})
    public ResponseEntity<ErrorResponse> handleAccessDenied(RuntimeException exception, HttpServletRequest request) {
        ErrorCode errorCode = ErrorCode.FORBIDDEN;
        ErrorResponse response = ErrorResponse.of(errorCode, errorCode.getMessage(), request.getRequestURI());
        return ResponseEntity.status(403).body(response);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(Exception exception, HttpServletRequest request) {
        log.error("Unhandled exception. path={}", request.getRequestURI(), exception);

        ErrorCode errorCode = ErrorCode.INTERNAL_SERVER_ERROR;
        ErrorResponse response = ErrorResponse.of(errorCode, errorCode.getMessage(), request.getRequestURI());
        return ResponseEntity.internalServerError().body(response);
    }

    /**
     * 요청 본문 DTO의 필드 검증 오류를 API 응답용 필드 오류로 변환한다.
     */
    private ErrorResponse.FieldError toFieldError(FieldError fieldError) {
        String message = resolveErrorMessage(fieldError);
        return new ErrorResponse.FieldError(fieldError.getField(), message);
    }

    /**
     * 검증 실패가 발생한 메서드 파라미터의 외부 노출 이름을 결정한다.
     */
    private String resolveParameterName(ParameterValidationResult result) {
        RequestParam requestParam = result.getMethodParameter().getParameterAnnotation(RequestParam.class);
        if (requestParam != null) {
            if (StringUtils.hasText(requestParam.name())) {
                return requestParam.name();
            }
            if (StringUtils.hasText(requestParam.value())) {
                return requestParam.value();
            }
        }

        RequestHeader requestHeader = result.getMethodParameter().getParameterAnnotation(RequestHeader.class);
        if (requestHeader != null) {
            if (StringUtils.hasText(requestHeader.name())) {
                return requestHeader.name();
            }
            if (StringUtils.hasText(requestHeader.value())) {
                return requestHeader.value();
            }
        }

        String parameterName = result.getMethodParameter().getParameterName();
        return StringUtils.hasText(parameterName) ? parameterName : "parameter";
    }

    /**
     * 검증 오류 메시지가 비어 있으면 모바일 클라이언트에 내려줄 기본 한글 메시지를 사용한다.
     */
    private String resolveErrorMessage(MessageSourceResolvable error) {
        String message = error.getDefaultMessage();
        return StringUtils.hasText(message) ? message : DEFAULT_FIELD_ERROR_MESSAGE;
    }
}
