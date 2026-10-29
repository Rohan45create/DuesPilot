package com.duespilot.api.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SecurityHeadersFilter Tests (Rule R-53, R-55, Section 16)")
class SecurityHeadersFilterTest {

    @Test
    @DisplayName("Applies strict CSP, nosniff, and frame denial (DENY) headers to all responses")
    void testSecurityHeadersApplied() throws Exception {
        SecurityHeadersFilter filter = new SecurityHeadersFilter();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/cases");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(response.getHeader("X-Frame-Options")).isEqualTo("DENY");
        assertThat(response.getHeader("Content-Security-Policy"))
            .contains("default-src 'self'")
            .contains("frame-ancestors 'none'")
            .contains("object-src 'none'")
            .contains("base-uri 'self'");
        assertThat(response.getHeader("Referrer-Policy")).isEqualTo("strict-origin-when-cross-origin");
    }
}
