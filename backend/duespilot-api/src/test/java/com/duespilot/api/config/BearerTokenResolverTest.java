package com.duespilot.api.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("BearerTokenResolver Unit Tests (F5)")
class BearerTokenResolverTest {

    @Test
    @DisplayName("Resolves Bearer token from Authorization header")
    void resolvesTokenFromAuthorizationHeader() {
        SecurityConfig config = new SecurityConfig();
        BearerTokenResolver resolver = config.bearerTokenResolver();

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer valid-jwt-token-12345");

        String token = resolver.resolve(request);
        assertThat(token).isEqualTo("valid-jwt-token-12345");
    }

    @Test
    @DisplayName("F5 fix: Rejects ?access_token= query parameter to prevent URI token leakage")
    void rejectsQueryParameterAccessToken() {
        SecurityConfig config = new SecurityConfig();
        BearerTokenResolver resolver = config.bearerTokenResolver();

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("access_token", "leaked-token-in-query-param");

        String token = resolver.resolve(request);
        assertThat(token).isNull();
    }
}
