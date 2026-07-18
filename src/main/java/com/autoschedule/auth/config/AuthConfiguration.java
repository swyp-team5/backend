package com.autoschedule.auth.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 인증 기능에서 사용하는 설정 프로퍼티 바인딩을 활성화한다.
 */
@Configuration
@EnableConfigurationProperties({JwtProperties.class, SocialAuthProperties.class})
public class AuthConfiguration {
}
