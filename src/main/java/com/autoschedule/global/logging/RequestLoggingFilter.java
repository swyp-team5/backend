package com.autoschedule.global.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 모든 HTTP 요청에 요청 ID를 부여하고 핵심 요청 정보를 한 줄 로그로 남긴다.
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestLoggingFilter extends OncePerRequestFilter {

    public static final String REQUEST_ID_HEADER = "X-Request-Id";
    private static final String REQUEST_ID_MDC_KEY = "requestId";
    private static final int MAX_REQUEST_ID_LENGTH = 64;
    private static final int MAX_HEADER_LOG_LENGTH = 200;
    private static final Pattern SAFE_REQUEST_ID = Pattern.compile("^[A-Za-z0-9._-]{1,64}$");

    /**
     * 요청 처리 전후로 요청 ID와 처리 시간, 상태 코드, 클라이언트 정보를 로깅한다.
     */
    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        long startedAt = System.nanoTime();
        String requestId = getOrCreateRequestId(request);
        MDC.put(REQUEST_ID_MDC_KEY, requestId);
        response.setHeader(REQUEST_ID_HEADER, requestId);
        Exception failure = null;

        try {
            filterChain.doFilter(request, response);
        } catch (IOException | ServletException | RuntimeException exception) {
            failure = exception;
            throw exception;
        } finally {
            long elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000;
            int status = getLogStatus(response, failure);
            log.info(
                    "http_request method={} uri={} status={} clientIp={} userAgent={} durationMs={}",
                    request.getMethod(),
                    request.getRequestURI(),
                    status,
                    getClientIp(request),
                    sanitizeHeaderValue(Optional.ofNullable(request.getHeader("User-Agent")).orElse("-")),
                    elapsedMillis
            );
            MDC.remove(REQUEST_ID_MDC_KEY);
        }
    }

    /**
     * 요청 헤더의 X-Request-Id를 검증하고, 없거나 안전하지 않으면 새 요청 ID를 생성한다.
     */
    private String getOrCreateRequestId(HttpServletRequest request) {
        String requestId = request.getHeader(REQUEST_ID_HEADER);
        if (requestId == null || requestId.isBlank() || !SAFE_REQUEST_ID.matcher(requestId).matches()) {
            return UUID.randomUUID().toString();
        }
        return requestId.length() > MAX_REQUEST_ID_LENGTH ? requestId.substring(0, MAX_REQUEST_ID_LENGTH) : requestId;
    }

    /**
     * 신뢰 가능한 프록시를 거친 요청이면 X-Forwarded-For를 사용하고, 아니면 직접 연결 IP를 사용한다.
     */
    private String getClientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank() && isTrustedProxyAddress(request.getRemoteAddr())) {
            return sanitizeHeaderValue(forwardedFor.split(",")[0].trim());
        }
        return sanitizeHeaderValue(Optional.ofNullable(request.getRemoteAddr()).orElse("-"));
    }

    /**
     * 필터 체인에서 예외가 발생했지만 응답 상태가 아직 오류가 아니면 로그 상태를 500으로 보정한다.
     */
    private int getLogStatus(HttpServletResponse response, Exception failure) {
        if (failure != null && response.getStatus() < HttpServletResponse.SC_BAD_REQUEST) {
            return HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
        }
        return response.getStatus();
    }

    /**
     * 로그 인젝션을 막기 위해 헤더 값을 한 줄로 정규화하고 길이를 제한한다.
     */
    private String sanitizeHeaderValue(String value) {
        String sanitized = value.replaceAll("[\\r\\n\\t]", "_");
        if (sanitized.length() <= MAX_HEADER_LOG_LENGTH) {
            return sanitized;
        }
        return sanitized.substring(0, MAX_HEADER_LOG_LENGTH);
    }

    /**
     * 프록시 헤더를 신뢰해도 되는 내부망, 루프백, 링크로컬 주소인지 확인한다.
     */
    private boolean isTrustedProxyAddress(String remoteAddress) {
        if (remoteAddress == null || remoteAddress.isBlank()) {
            return false;
        }

        try {
            InetAddress address = InetAddress.getByName(remoteAddress);
            return address.isAnyLocalAddress()
                    || address.isLoopbackAddress()
                    || address.isLinkLocalAddress()
                    || address.isSiteLocalAddress()
                    || isUniqueLocalIpv6Address(address);
        } catch (UnknownHostException exception) {
            return false;
        }
    }

    /**
     * IPv6 ULA(fc00::/7) 주소인지 확인해 사설 네트워크 프록시로 취급한다.
     */
    private boolean isUniqueLocalIpv6Address(InetAddress address) {
        if (!(address instanceof Inet6Address)) {
            return false;
        }
        byte firstByte = address.getAddress()[0];
        return (firstByte & 0xfe) == 0xfc;
    }
}
