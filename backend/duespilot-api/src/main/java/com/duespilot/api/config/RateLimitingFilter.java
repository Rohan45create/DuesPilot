package com.duespilot.api.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Enforces per-user and per-IP rate limiting per Rule R-54 and Architecture section 16:
 * "Per-user and per-IP rate limiting on live mode before the URL is public."
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class RateLimitingFilter extends OncePerRequestFilter {

    private final DuesPilotProperties duesPilotProperties;

    private static class RateWindow {
        final long minuteTimestamp;
        final AtomicInteger count;

        RateWindow(long minuteTimestamp, int initialCount) {
            this.minuteTimestamp = minuteTimestamp;
            this.count = new AtomicInteger(initialCount);
        }
    }

    private final Map<String, RateWindow> requestCounts = new ConcurrentHashMap<>();
    private Integer testOverrideLimit = null;

    public RateLimitingFilter(DuesPilotProperties duesPilotProperties) {
        this.duesPilotProperties = duesPilotProperties;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        // Do not rate-limit static assets, health probes or actuator endpoints
        return uri.startsWith("/actuator") || uri.endsWith(".js") || uri.endsWith(".css") || uri.endsWith(".svg") || uri.endsWith(".png");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        // Rule R-54: Rate limits enforced on live mode (or when test override set)
        boolean enforce = duesPilotProperties.isLiveMode() || testOverrideLimit != null;
        if (!enforce) {
            filterChain.doFilter(request, response);
            return;
        }

        String clientIp = resolveClientIp(request);
        int maxRpm = testOverrideLimit != null ? testOverrideLimit : duesPilotProperties.getRateLimit().getRequestsPerMinute();

        long currentMinute = System.currentTimeMillis() / 60000;

        // 1. Track per-IP
        RateWindow ipWindow = requestCounts.compute("ip:" + clientIp, (k, current) -> {
            if (current == null || current.minuteTimestamp != currentMinute) {
                return new RateWindow(currentMinute, 1);
            }
            current.count.incrementAndGet();
            return current;
        });

        // 2. Track per-user if authenticated
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        RateWindow userWindow = null;
        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
            String userId = auth.getName();
            userWindow = requestCounts.compute("user:" + userId, (k, current) -> {
                if (current == null || current.minuteTimestamp != currentMinute) {
                    return new RateWindow(currentMinute, 1);
                }
                current.count.incrementAndGet();
                return current;
            });
        }

        if (ipWindow.count.get() > maxRpm || (userWindow != null && userWindow.count.get() > maxRpm)) {
            response.setStatus(429);
            response.setHeader("Content-Type", "application/problem+json");
            response.setHeader("Retry-After", "60");
            response.getWriter().write("""
                {
                  "type": "https://duespilot.com/errors/rate_limited",
                  "title": "Rate Limit Exceeded",
                  "status": 429,
                  "detail": "Rate limit exceeded. Please wait before retrying.",
                  "code": "RATE_LIMITED"
                }
                """.trim());
            return;
        }

        filterChain.doFilter(request, response);
    }

    public static String resolveClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr() != null ? request.getRemoteAddr() : "127.0.0.1";
    }

    public void setTestOverrideLimit(Integer limit) {
        this.testOverrideLimit = limit;
    }

    public void resetCounts() {
        this.requestCounts.clear();
        this.testOverrideLimit = null;
    }
}
