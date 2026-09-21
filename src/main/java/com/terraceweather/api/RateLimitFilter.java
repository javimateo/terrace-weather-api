package com.terraceweather.api;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Fixed-window rate limit per client IP on {@code /api/**}, protecting the free Open-Meteo quota.
 * Behind a reverse proxy the client IP comes from X-Forwarded-For (server.forward-headers-strategy=native).
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final long WINDOW_MILLIS = 60_000;
    private static final int PURGE_THRESHOLD = 10_000;

    private record Window(long start, int count) {
    }

    private final int limit;
    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    public RateLimitFilter(@Value("${terrace.rate-limit.requests-per-minute:60}") int limit) {
        this.limit = limit;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long now = System.currentTimeMillis();
        if (windows.size() > PURGE_THRESHOLD) {
            windows.values().removeIf(w -> now - w.start() >= WINDOW_MILLIS);
        }
        Window w = windows.compute(request.getRemoteAddr(),
                (ip, cur) -> cur == null || now - cur.start() >= WINDOW_MILLIS
                        ? new Window(now, 1)
                        : new Window(cur.start(), cur.count() + 1));

        response.setHeader("X-RateLimit-Limit", String.valueOf(limit));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(Math.max(0, limit - w.count())));

        if (w.count() > limit) {
            long retryAfter = Math.max(1, (w.start() + WINDOW_MILLIS - now + 999) / 1000);
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setHeader("Retry-After", String.valueOf(retryAfter));
            response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            response.getWriter().write("{\"status\":429,\"title\":\"Too Many Requests\","
                    + "\"detail\":\"Rate limit of " + limit + " requests per minute exceeded\"}");
            return;
        }
        chain.doFilter(request, response);
    }
}
