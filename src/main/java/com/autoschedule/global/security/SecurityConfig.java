package com.autoschedule.global.security;

import com.autoschedule.global.exception.ErrorCode;
import com.autoschedule.global.exception.ErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * REST API용 Spring Security 정책을 구성한다.
 */
@Configuration
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final ObjectMapper objectMapper;


    /**
     * 인증 API와 문서/헬스체크는 허용하고, 나머지 API는 JWT 인증을 요구한다.
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/terms/signup").permitAll()
                        .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/actuator/health").permitAll()
                        .anyRequest().authenticated()
                )
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint((request, response, authException) -> writeSecurityError(
                                request,
                                response,
                                ErrorCode.UNAUTHORIZED,
                                HttpServletResponse.SC_UNAUTHORIZED,
                                "인증이 필요합니다."
                        ))
                        .accessDeniedHandler((request, response, accessDeniedException) -> writeSecurityError(
                                request,
                                response,
                                ErrorCode.FORBIDDEN,
                                HttpServletResponse.SC_FORBIDDEN,
                                "접근 권한이 없습니다."
                        ))
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    /**
     * Spring Security 필터 체인에서 발생한 인증/인가 실패를 공통 JSON 오류 응답으로 작성한다.
     */
    private void writeSecurityError(
            HttpServletRequest request,
            HttpServletResponse response,
            ErrorCode errorCode,
            int httpStatus,
            String message
    ) throws IOException {
        ErrorResponse errorResponse = ErrorResponse.of(errorCode, message, request.getRequestURI());
        response.setStatus(httpStatus);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), errorResponse);
    }

}
