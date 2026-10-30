package com.duespilot.api.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import java.util.Arrays;
import java.util.List;

/**
 * Spring Security OAuth2 Resource Server Configuration for Supabase Auth.
 *
 * Validates JWTs issued by Supabase Auth via JWKS URI:
 *   ${SUPABASE_URL}/auth/v1/.well-known/jwks.json
 * Validates issuer (${SUPABASE_URL}/auth/v1) and audience ("authenticated").
 * Reads sub, email, and is_anonymous claims.
 *
 * Legacy JWT Secret Migration Step:
 *   Supabase projects previously used a symmetric HMAC secret (jwt_secret). To use standard RS256/ECC
 *   asymmetric verification without embedding secrets in the application binary:
 *   1. Go to Supabase Dashboard -> Project Settings -> API / Auth.
 *   2. Ensure Asymmetric Signing Keys are enabled.
 *   3. The JWKS endpoint is published at ${SUPABASE_URL}/auth/v1/.well-known/jwks.json.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

    @Value("${duespilot.auth.supabase-url:}")
    private String supabaseUrl;

    @Autowired
    private Environment environment;

    @PostConstruct
    public void validateSupabaseConfiguration() {
        boolean isTest = java.util.Arrays.asList(environment.getActiveProfiles()).contains("test");
        if (!isTest) {
            if (supabaseUrl == null || supabaseUrl.isBlank() || supabaseUrl.contains("YOUR-PROJECT-REF") || supabaseUrl.contains("mock.supabase.co")) {
                throw new IllegalStateException(
                    "CRITICAL STARTUP ERROR: SUPABASE_URL environment variable is required and must point to a valid Supabase project (e.g. https://<project-ref>.supabase.co)."
                );
            }
        }
    }

    @Value("${duespilot.auth.jwks-uri:}")
    private String customJwksUri;

    @Value("${duespilot.cors.allowed-origins:http://localhost:5173,http://localhost:3000,http://localhost:8080}")
    private String corsAllowedOrigins;

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            CorsConfigurationSource corsConfigurationSource,
            BearerTokenResolver bearerTokenResolver,
            JwtDecoder jwtDecoder,
            RateLimitingFilter rateLimitingFilter) throws Exception {

        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource))
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // Public endpoints: health probes, ping, webhooks, setup guide
                // F8 fix: expose only /actuator/health publicly; wildcard /actuator/** removed.
                .requestMatchers(HttpMethod.GET, "/api/v1/ping", "/actuator/health", "/api/v1/notifications/whatsapp/setup-guide").permitAll()
                .requestMatchers("/api/v1/webhooks/**").permitAll()
                // Require authenticated token for all other API routes (including SSE streaming endpoints)
                .requestMatchers("/api/v1/**").authenticated()
                .anyRequest().permitAll()
            )
            .oauth2ResourceServer(oauth2 -> oauth2
                .bearerTokenResolver(bearerTokenResolver)
                .jwt(jwt -> jwt.decoder(jwtDecoder))
                .authenticationEntryPoint((request, response, authException) -> {
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    response.getWriter().write("""
                        {
                          "type": "https://duespilot.com/errors/unauthorized",
                          "title": "Unauthorized",
                          "status": 401,
                          "code": "UNAUTHORIZED",
                          "detail": "Invalid, expired, or missing Bearer token.",
                          "message": "Invalid, expired, or missing Bearer token."
                        }
                        """);
                })
            )
            // F6 fix: RateLimitingFilter runs AFTER BearerTokenAuthenticationFilter so authenticated user identity is available
            .addFilterAfter(rateLimitingFilter, BearerTokenAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public BearerTokenResolver bearerTokenResolver() {
        DefaultBearerTokenResolver resolver = new DefaultBearerTokenResolver();
        // F5 fix: Tokens must come ONLY from the Authorization header.
        // Query parameter tokens (?access_token=) are explicitly disabled to prevent token leakage in URLs and logs.
        resolver.setAllowUriQueryParameter(false);
        return resolver;
    }

    @Bean
    public JwtDecoder jwtDecoder() {
        String baseUrl = supabaseUrl.replaceAll("/+$", "");
        String jwksUri = (customJwksUri != null && !customJwksUri.isBlank())
            ? customJwksUri
            : baseUrl + "/auth/v1/.well-known/jwks.json";
        String expectedIssuer = baseUrl + "/auth/v1";

        log.info("Configuring Supabase OAuth2 Resource Server. JWKS: {}, Issuer: {}, Audience: authenticated",
            jwksUri, expectedIssuer);

        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwksUri)
            .jwsAlgorithms(algorithms -> {
                algorithms.add(SignatureAlgorithm.RS256);
                algorithms.add(SignatureAlgorithm.ES256);
            })
            .build();

        OAuth2TokenValidator<Jwt> withIssuer = JwtValidators.createDefaultWithIssuer(expectedIssuer);
        OAuth2TokenValidator<Jwt> withAudience = new JwtClaimValidator<List<String>>("aud",
            aud -> aud != null && aud.contains("authenticated"));

        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(withIssuer, withAudience));
        return decoder;
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        String envOrigins = System.getenv("CORS_ALLOWED_ORIGINS");
        String originsStr = (envOrigins != null && !envOrigins.isBlank()) ? envOrigins : corsAllowedOrigins;

        List<String> origins = Arrays.stream(originsStr.split(","))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .toList();

        CorsConfiguration config = new CorsConfiguration();
        for (String origin : origins) {
            if (origin.contains("*")) {
                config.addAllowedOriginPattern(origin);
            } else {
                config.addAllowedOrigin(origin);
            }
        }
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS", "HEAD"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept", "Origin", "X-Requested-With"));
        config.setExposedHeaders(List.of("Content-Disposition", "Content-Type", "Content-Length"));
        config.setAllowCredentials(false); // Stateless Bearer token architecture
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    public org.springframework.boot.web.servlet.FilterRegistrationBean<RateLimitingFilter> rateLimitingFilterRegistration(RateLimitingFilter filter) {
        org.springframework.boot.web.servlet.FilterRegistrationBean<RateLimitingFilter> registration =
            new org.springframework.boot.web.servlet.FilterRegistrationBean<>(filter);
        registration.setEnabled(false); // Managed exclusively by Spring Security filter chain
        return registration;
    }
}
