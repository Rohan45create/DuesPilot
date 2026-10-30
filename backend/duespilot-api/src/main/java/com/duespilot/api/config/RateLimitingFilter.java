package com.duespilot.api.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Enforces per-user and per-IP rate limiting per Rule R-54 and Architecture section 16.
 * F6 hardening:
 * - Enforced across all modes (live, replay, demo) for API endpoints.
 * - Runs AFTER authentication so SecurityContext holds the authenticated user ID.
 * - Safely derives client IP from request.getRemoteAddr() (backed by server.forward-headers-strategy=framework).
 * - Evicts expired windows periodically to prevent memory leaks.
 */
@Component
public class RateLimitingFilter extends OncePerRequestFilter {

    private final DuesPilotProperties duesPilotProperties;

    public static class RateWindow {
        final long minuteTimestamp;
        final AtomicInteger count;

        public RateWindow(long minuteTimestamp, int initialCount) {
            this.minuteTimestamp = minuteTimestamp;
            this.count = new AtomicInteger(initialCount);
        }

        public long getMinuteTimestamp() {
            return minuteTimestamp;
        }

        public int getCount() {
            return count.get();
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
        return uri.startsWith("/actuator")
            || uri.equals("/api/v1/ping")
            || uri.endsWith(".js")
            || uri.endsWith(".css")
            || uri.endsWith(".svg")
            || uri.endsWith(".png")
            || uri.endsWith(".ico")
            || uri.endsWith(".html");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        // F6: Scheduled or periodic eviction of expired windows
        evictExpiredWindowsIfStale();

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

        // 2. Track per-user if authenticated (runs AFTER authentication)
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

    /**
     * Resolves the verified client IP safely.
     * With server.forward-headers-strategy=framework configured in application.yml,
     * Spring's ForwardedHeaderFilter safely updates request.getRemoteAddr() using trusted proxy headers.
     * Never trusts raw untrusted client-supplied X-Forwarded-For headers directly.
     */
    public static String resolveClientIp(HttpServletRequest request) {
        String remoteAddr = request.getRemoteAddr();
        return (remoteAddr != null && !remoteAddr.isBlank()) ? remoteAddr : "127.0.0.1";
    }

    @Scheduled(fixedRate = 60000)
    public void evictExpiredWindows() {
        long currentMinute = System.currentTimeMillis() / 60000;
        requestCounts.entrySet().removeIf(entry -> entry.getValue().minuteTimestamp < currentMinute);
    }

    private void evictExpiredWindowsIfStale() {
        if (requestCounts.size() > 1000) {
            evictExpiredWindows();
        }
    }

    public void setTestOverrideLimit(Integer limit) {
        this.testOverrideLimit = limit;
    }

    public void resetCounts() {
        this.requestCounts.clear();
        this.testOverrideLimit = null;
    }

    public Map<String, RateWindow> getRequestCounts() {
        return requestCounts;
    }
}
