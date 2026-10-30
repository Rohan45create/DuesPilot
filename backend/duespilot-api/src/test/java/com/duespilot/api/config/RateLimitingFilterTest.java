package com.duespilot.api.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("RateLimitingFilter Hardened Tests (F6, Rule R-54)")
class RateLimitingFilterTest {

    private DuesPilotProperties properties;
    private RateLimitingFilter filter;

    @BeforeEach
    void setUp() {
        properties = new DuesPilotProperties();
        properties.setDemoMode("replay"); // F6: must enforce in ALL modes including replay
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
    @DisplayName("Requests exceeding limit return HTTP 429 with RATE_LIMITED ProblemDetail in all modes")
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
    @DisplayName("F6: Derives client IP safely from resolved remote address, ignoring spoofed X-Forwarded-For headers")
    void testSafeRemoteAddressDerivation() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRemoteAddr("198.51.100.42");
        // An attacker might try to spoof X-Forwarded-For:
        req.addHeader("X-Forwarded-For", "1.2.3.4, 5.6.7.8");

        String resolved = RateLimitingFilter.resolveClientIp(req);
        assertThat(resolved).isEqualTo("198.51.100.42");
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

    @Test
    @DisplayName("F6: Eviction purges expired windows older than current minute")
    void testEvictionOfExpiredWindows() {
        long oldMinute = (System.currentTimeMillis() / 60000) - 2;
        filter.getRequestCounts().put("ip:old.ip", new RateLimitingFilter.RateWindow(oldMinute, 10));

        long currentMinute = System.currentTimeMillis() / 60000;
        filter.getRequestCounts().put("ip:current.ip", new RateLimitingFilter.RateWindow(currentMinute, 2));

        assertThat(filter.getRequestCounts()).containsKey("ip:old.ip");
        assertThat(filter.getRequestCounts()).containsKey("ip:current.ip");

        filter.evictExpiredWindows();

        assertThat(filter.getRequestCounts()).doesNotContainKey("ip:old.ip");
        assertThat(filter.getRequestCounts()).containsKey("ip:current.ip");
    }
}
