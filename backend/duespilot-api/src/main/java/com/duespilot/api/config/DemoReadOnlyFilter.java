package com.duespilot.api.config;

import com.duespilot.api.service.CurrentUserService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Rejects any write (POST, PUT, PATCH, DELETE) attempted by an anonymous demo user
 * with HTTP 403, code DEMO_READ_ONLY and instructions to sign up.
 */
@Component
public class DemoReadOnlyFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jwtAuth) {
            Jwt jwt = jwtAuth.getToken();
            boolean isAnonymous = CurrentUserService.isAnonymousToken(jwt);

            String method = request.getMethod();
            boolean isWrite = "POST".equalsIgnoreCase(method)
                || "PUT".equalsIgnoreCase(method)
                || "PATCH".equalsIgnoreCase(method)
                || "DELETE".equalsIgnoreCase(method);

            if (isAnonymous && isWrite) {
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                response.getWriter().write("""
                    {
                      "type": "https://duespilot.com/errors/demo_read_only",
                      "title": "Forbidden",
                      "status": 403,
                      "code": "DEMO_READ_ONLY",
                      "detail": "Demo workspace is read-only. Please create an account or sign in to save changes.",
                      "message": "Demo workspace is read-only. Please create an account or sign in to save changes."
                    }
                    """);
                return;
            }
        }

        filterChain.doFilter(request, response);
    }
}
