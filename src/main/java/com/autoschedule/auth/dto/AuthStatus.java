package com.autoschedule.auth.dto;

/**
 * 인증 API 응답에서 앱이 다음 화면을 결정하는 상태값이다.
 */
public enum AuthStatus {
    LOGIN_SUCCESS,
    SIGNUP_REQUIRED
}
