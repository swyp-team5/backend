package com.autoschedule.global.security.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.security.access.prepost.PreAuthorize;

/**
 * OWNER 권한을 가진 회원만 접근할 수 있는 컨트롤러 클래스 또는 메서드임을 선언한다.
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@PreAuthorize("hasRole('OWNER')")
public @interface OwnerOnly {
}
