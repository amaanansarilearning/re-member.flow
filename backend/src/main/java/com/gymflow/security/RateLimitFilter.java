package com.gymflow.security;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class RateLimitFilter extends OncePerRequestFilter {
    private static final int MAX_BUCKETS = 10_000;
    private final Map<String, Bucket> buckets = java.util.Collections.synchronizedMap(
            new java.util.LinkedHashMap<>(1024, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Bucket> eldest) {
                    return size() > MAX_BUCKETS;
                }
            });

    @org.springframework.beans.factory.annotation.Value("${gym.security.trust-proxy:false}")
    private boolean trustProxy;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getRequestURI();
        if (path.startsWith("/api/auth/")) {
            String key = clientIp(request) + ":" + path;
            Bucket bucket = buckets.computeIfAbsent(key, k -> createBucket(path));
            if (bucket.tryConsume(1)) {
                chain.doFilter(request, response);
            } else {
                response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"message\":\"Too many requests. Please try again later.\"}");
            }
            return;
        }

        // Apply rate limiting to all other API endpoints
        if (path.startsWith("/api/")) {
            String key = clientIp(request) + ":api";
            Bucket bucket = buckets.computeIfAbsent(key, k -> createBucket("/api/"));
            if (!bucket.tryConsume(1)) {
                response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"message\":\"Too many requests. Please try again later.\"}");
                return;
            }
        }
        
        chain.doFilter(request, response);
    }

    private Bucket createBucket(String path) {
        if (path.contains("/login")) {
            return Bucket.builder()
                    .addLimit(Bandwidth.classic(5, Refill.intervally(5, Duration.ofMinutes(1))))
                    .addLimit(Bandwidth.classic(20, Refill.intervally(20, Duration.ofHours(1))))
                    .build();
        }
        if (path.contains("/password-reset")) {
            return Bucket.builder()
                    .addLimit(Bandwidth.classic(3, Refill.intervally(3, Duration.ofMinutes(5))))
                    .addLimit(Bandwidth.classic(10, Refill.intervally(10, Duration.ofHours(1))))
                    .build();
        }
        return Bucket.builder()
                .addLimit(Bandwidth.classic(200, Refill.intervally(200, Duration.ofMinutes(1))))
                .build();
    }

    private String clientIp(HttpServletRequest request) {
        if (trustProxy) {
            String xff = request.getHeader("X-Forwarded-For");
            if (xff != null && !xff.isBlank()) {
                return xff.split(",")[0].trim();
            }
        }
        return request.getRemoteAddr();
    }
}
