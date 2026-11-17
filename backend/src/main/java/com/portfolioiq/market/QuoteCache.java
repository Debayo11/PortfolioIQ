package com.portfolioiq.market;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

/**
 * Short-lived Redis cache of latest quotes. Fails open: if Redis is unavailable the app simply fetches
 * from the provider, so a cache outage never takes market data down.
 */
@Component
public class QuoteCache {
    private static final Logger log = LoggerFactory.getLogger(QuoteCache.class);

    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;
    private final Duration ttl;

    public QuoteCache(StringRedisTemplate redis, ObjectMapper mapper,
                      @Value("${app.market.cache-ttl-seconds:60}") long ttlSeconds) {
        this.redis = redis;
        this.mapper = mapper;
        this.ttl = Duration.ofSeconds(ttlSeconds);
    }

    public Optional<Quote> get(Long assetId) {
        try {
            String json = redis.opsForValue().get(key(assetId));
            return json == null ? Optional.empty() : Optional.of(mapper.readValue(json, Quote.class));
        } catch (Exception e) {
            log.debug("Quote cache read failed: {}", e.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    public void put(Long assetId, Quote quote) {
        try {
            redis.opsForValue().set(key(assetId), mapper.writeValueAsString(quote), ttl);
        } catch (Exception e) {
            log.debug("Quote cache write failed: {}", e.getClass().getSimpleName());
        }
    }

    private static String key(Long assetId) { return "quote:" + assetId; }
}
