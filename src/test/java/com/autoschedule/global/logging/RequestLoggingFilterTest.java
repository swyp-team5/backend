package com.autoschedule.global.logging;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * 요청 로깅 필터의 요청 ID 생성, 로그 포맷, 클라이언트 IP 정책을 검증한다.
 */
@ExtendWith(OutputCaptureExtension.class)
class RequestLoggingFilterTest {

    private final RequestLoggingFilter filter = new RequestLoggingFilter();

    /**
     * 요청 ID 헤더가 없으면 새 요청 ID를 응답 헤더에 내려주는지 검증한다.
     */
    @Test
    void createsRequestIdWhenHeaderIsMissing() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/test");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (servletRequest, servletResponse) -> {
        };

        filter.doFilter(request, response, chain);

        assertThat(response.getHeader(RequestLoggingFilter.REQUEST_ID_HEADER)).isNotBlank();
    }

    /**
     * 안전한 요청 ID 헤더가 들어오면 같은 값을 응답 헤더에 재사용하는지 검증한다.
     */
    @Test
    void reusesIncomingRequestId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/test");
        request.addHeader(RequestLoggingFilter.REQUEST_ID_HEADER, "request-id-123");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (servletRequest, servletResponse) -> {
        };

        filter.doFilter(request, response, chain);

        assertThat(response.getHeader(RequestLoggingFilter.REQUEST_ID_HEADER)).isEqualTo("request-id-123");
    }

    /**
     * 로그 인젝션 위험이 있는 요청 ID는 폐기하고 새 요청 ID로 교체하는지 검증한다.
     */
    @Test
    void replacesUnsafeIncomingRequestId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/test");
        request.addHeader(RequestLoggingFilter.REQUEST_ID_HEADER, "unsafe\r\nrequest-id");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (servletRequest, servletResponse) -> {
        };

        filter.doFilter(request, response, chain);

        assertThat(response.getHeader(RequestLoggingFilter.REQUEST_ID_HEADER))
                .isNotBlank()
                .isNotEqualTo("unsafe\r\nrequest-id");
    }

    /**
     * 요청 로그 본문에 쿼리 문자열과 중복 requestId 필드가 남지 않는지 검증한다.
     */
    @Test
    void doesNotLogQueryStringOrDuplicateRequestIdInMessageBody(CapturedOutput output) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/test");
        request.setQueryString("token=secret");
        request.addHeader(RequestLoggingFilter.REQUEST_ID_HEADER, "request-id-123");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (servletRequest, servletResponse) -> {
        };

        filter.doFilter(request, response, chain);

        assertThat(output).contains("http_request method=GET uri=/api/test");
        assertThat(output).doesNotContain("token=secret");
        assertThat(output).doesNotContain("requestId=request-id-123");
    }

    /**
     * 요청 처리 시간이 로그 메시지의 마지막 필드로 출력되는지 검증한다.
     */
    @Test
    void placesDurationAtEndOfRequestLogMessage(CapturedOutput output) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/test");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (servletRequest, servletResponse) -> {
        };

        filter.doFilter(request, response, chain);

        assertThat(output.getOut()).containsPattern("http_request .* userAgent=- durationMs=\\d+\\R");
    }

    /**
     * 신뢰되지 않은 직접 연결 요청의 X-Forwarded-For 값을 clientIp로 사용하지 않는지 검증한다.
     */
    @Test
    void ignoresForwardedForFromUntrustedRemoteAddress(CapturedOutput output) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/test");
        request.setRemoteAddr("203.0.113.10");
        request.addHeader("X-Forwarded-For", "198.51.100.20");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (servletRequest, servletResponse) -> {
        };

        filter.doFilter(request, response, chain);

        assertThat(output).contains("clientIp=203.0.113.10");
        assertThat(output).doesNotContain("clientIp=198.51.100.20");
    }
}
