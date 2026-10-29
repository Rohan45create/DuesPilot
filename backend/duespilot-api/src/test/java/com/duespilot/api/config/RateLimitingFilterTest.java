package com.duespilot.api.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("RateLimitingFilter Tests (Rule R-54, Section 16)")
class RateLimitingFilterTest {

    private DuesPilotProperties properties;
    private RateLimitingFilter filter;

    @BeforeEach
    void setUp() {
        properties = new DuesPilotProperties();
        properties.setDemoMode("live"); // live mode enforces rate limits
        properties.getRateLimit().setRequestsPerMinute(5);
        filter = new RateLimitingFilter(properties);
    }

    @Test
    @DisplayName("Requests within limit pass through with HTTP 200")
    void testRequestsWithinLimitPass() throws Exception {
        for (int i = 0; i < 5; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/cases/test-case/analyze");
            request.setRemoteAddr("192.168.1.100");
            MockHttpServletResponse response = new MockHttpServletResponse();
            MockFilterChain chain = new MockFilterChain();

            filter.doFilter(request, response, chain);
            assertThat(response.getStatus()).isEqualTo(200);
        }
    }

    @Test
    @DisplayName("Requests exceeding limit return HTTP 429 with RATE_LIMITED ProblemDetail")
    void testRequestExceedingLimitReturns429() throws Exception {
        // Send 5 permitted requests
        for (int i = 0; i < 5; i++) {
            MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/v1/cases/test-case/analyze");
            req.setRemoteAddr("10.0.0.5");
            MockHttpServletResponse res = new MockHttpServletResponse();
            filter.doFilter(req, res, new MockFilterChain());
            assertThat(res.getStatus()).isEqualTo(200);
        }

        // 6th request from same IP must be rate limited
        MockHttpServletRequest blockedReq = new MockHttpServletRequest("POST", "/api/v1/cases/test-case/analyze");
        blockedReq.setRemoteAddr("10.0.0.5");
        MockHttpServletResponse blockedRes = new MockHttpServletResponse();

        filter.doFilter(blockedReq, blockedRes, new MockFilterChain());

        assertThat(blockedRes.getStatus()).isEqualTo(429);
        assertThat(blockedRes.getHeader("Content-Type")).contains("application/problem+json");
        assertThat(blockedRes.getHeader("Retry-After")).isEqualTo("60");
        assertThat(blockedRes.getContentAsString()).contains("\"code\": \"RATE_LIMITED\"");
        assertThat(blockedRes.getContentAsString()).contains("Rate limit exceeded");
    }

    @Test
    @DisplayName("Different IPs have independent rate limit windows")
    void testIndependentIpLimits() throws Exception {
        // Fill up IP A
        for (int i = 0; i < 5; i++) {
            MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/v1/cases");
            req.setRemoteAddr("1.1.1.1");
            MockHttpServletResponse res = new MockHttpServletResponse();
            filter.doFilter(req, res, new MockFilterChain());
        }

        // IP A is now blocked
        MockHttpServletRequest reqA = new MockHttpServletRequest("POST", "/api/v1/cases");
        reqA.setRemoteAddr("1.1.1.1");
        MockHttpServletResponse resA = new MockHttpServletResponse();
        filter.doFilter(reqA, resA, new MockFilterChain());
        assertThat(resA.getStatus()).isEqualTo(429);

        // IP B should still be allowed
        MockHttpServletRequest reqB = new MockHttpServletRequest("POST", "/api/v1/cases");
        reqB.setRemoteAddr("2.2.2.2");
        MockHttpServletResponse resB = new MockHttpServletResponse();
        filter.doFilter(reqB, resB, new MockFilterChain());
        assertThat(resB.getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("X-Forwarded-For header is parsed to determine client IP")
    void testXForwardedForHeaderResolution() throws Exception {
        for (int i = 0; i < 5; i++) {
            MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/v1/cases");
            req.addHeader("X-Forwarded-For", "203.0.113.195, 70.41.3.18");
            MockHttpServletResponse res = new MockHttpServletResponse();
            filter.doFilter(req, res, new MockFilterChain());
            assertThat(res.getStatus()).isEqualTo(200);
        }

        MockHttpServletRequest blockedReq = new MockHttpServletRequest("POST", "/api/v1/cases");
        blockedReq.addHeader("X-Forwarded-For", "203.0.113.195, 70.41.3.18");
        MockHttpServletResponse blockedRes = new MockHttpServletResponse();
        filter.doFilter(blockedReq, blockedRes, new MockFilterChain());

        assertThat(blockedRes.getStatus()).isEqualTo(429);
    }

    @Test
    @DisplayName("Per-user rate limit enforces limit across different IPs for the same authenticated user")
    void testPerUserRateLimitAcrossIps() throws Exception {
        org.springframework.security.core.Authentication auth =
            new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                "user-sub-123", "n/a", java.util.List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_USER"))
            );
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(auth);

        try {
            // Send 5 requests from 5 different IPs
            for (int i = 0; i < 5; i++) {
                MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/v1/cases");
                req.setRemoteAddr("10.0.1." + i);
                MockHttpServletResponse res = new MockHttpServletResponse();
                filter.doFilter(req, res, new MockFilterChain());
                assertThat(res.getStatus()).isEqualTo(200);
            }

            // 6th request from yet another new IP, but same user -> must be 429
            MockHttpServletRequest reqBlocked = new MockHttpServletRequest("POST", "/api/v1/cases");
            reqBlocked.setRemoteAddr("10.0.1.99");
            MockHttpServletResponse resBlocked = new MockHttpServletResponse();
            filter.doFilter(reqBlocked, resBlocked, new MockFilterChain());

            assertThat(resBlocked.getStatus()).isEqualTo(429);
            assertThat(resBlocked.getContentAsString()).contains("\"code\": \"RATE_LIMITED\"");
        } finally {
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
        }
    }
}
