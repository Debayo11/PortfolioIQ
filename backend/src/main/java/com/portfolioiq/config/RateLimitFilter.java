package com.portfolioiq.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolioiq.common.ApiError;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Fixed-window rate limiting in Redis, keyed by client address. Sign-in, registration and password endpoints
 * get a much lower limit to slow credential guessing. Fails open if Redis is unavailable.
 * Behind a reverse proxy, configure the proxy and server.forward-headers-strategy so the real client address is used.
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;
    private final boolean enabled;
    private final int authLimit;
    private final int apiLimit;
    private final AtomicBoolean redisDownLogged = new AtomicBoolean(false);

    public RateLimitFilter(StringRedisTemplate redis, ObjectMapper mapper,
                           @Value("${app.rate-limit.enabled:true}") boolean enabled,
                           @Value("${app.rate-limit.auth-per-minute:10}") int authLimit,
                           @Value("${app.rate-limit.api-per-minute:120}") int apiLimit) {
        this.redis = redis;
        this.mapper = mapper;
        this.enabled = enabled;
        this.authLimit = authLimit;
        this.apiLimit = apiLimit;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !enabled || !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        boolean authBucket = req.getRequestURI().startsWith("/api/auth/") && "POST".equals(req.getMethod());
        int limit = authBucket ? authLimit : apiLimit;
        long now = Instant.now().getEpochSecond();
        String key = "rl:" + (authBucket ? "auth" : "api") + ":" + req.getRemoteAddr() + ":" + (now / 60);
        try {
            Long count = redis.opsForValue().increment(key);
            redisDownLogged.set(false);
            if (count != null && count == 1L) redis.expire(key, Duration.ofSeconds(120));
            if (count != null && count > limit) {
                res.setStatus(429);
                res.setHeader("Retry-After", String.valueOf(60 - (now % 60)));
                res.setHeader("X-RateLimit-Limit", String.valueOf(limit));
                res.setContentType("application/json");
                res.getWriter().write(mapper.writeValueAsString(ApiError.of(429, "Too Many Requests",
                        "Rate limit exceeded. Please try again shortly.", List.of())));
                return;
            }
        } catch (Exception e) {
            if (redisDownLogged.compareAndSet(false, true)) {
                log.warn("Rate limiting unavailable (Redis error: {}); allowing requests", e.getClass().getSimpleName());
            }
        }
        chain.doFilter(req, res);
    }
}
