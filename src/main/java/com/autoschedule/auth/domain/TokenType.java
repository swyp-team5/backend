package com.autoschedule.auth.domain;

/**
 * 우리 서버가 발급하는 JWT의 용도를 구분한다.
 */
public enum TokenType {
    ACCESS,
    REFRESH
}
