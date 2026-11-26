package com.fixops.member.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.json.JsonMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.*;

@Component
public class KaptSearchCache {
    private static final Logger log = LoggerFactory.getLogger(KaptSearchCache.class);
    private static final Duration TTL = Duration.ofMinutes(5);
    private static final Duration EMPTY_TTL = Duration.ofMinutes(1);
    private static final int MAX_JSON_CHARS = 256 * 1024;
    private final StringRedisTemplate redis;
    private final JsonMapper mapper = JsonMapper.builder().build();

    public KaptSearchCache(StringRedisTemplate redis) { this.redis = redis; }

    static String key(String version, String region, String normalizedQuery, int page, int size) {
        try {
            var digest = MessageDigest.getInstance("SHA-256")
                .digest(normalizedQuery.getBytes(StandardCharsets.UTF_8));
            return "fixops:kapt:search:v1:" + version + ":" + region + ":" +
                HexFormat.of().formatHex(digest) + ":" + page + ":" + size;
        } catch (java.security.NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    Optional<KaptClient.Search> get(String key, int page, int size) {
        try {
            String json = redis.opsForValue().get(key);
            if (json == null || json.length() > MAX_JSON_CHARS) return Optional.empty();
            var result = mapper.readValue(json, KaptClient.Search.class);
            if (result == null || result.items() == null || result.page() != page
                || result.totalCount() < 0 || result.items().size() > size
                || result.items().size() != Math.min(size, Math.max(0L, result.totalCount() - (page-1L)*size)))
                return Optional.empty();
            return Optional.of(result);
        } catch (Exception ex) {
            log.debug("KAPT search cache read failed: type={}", ex.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    void put(String key, KaptClient.Search result) {
        try {
            String json = mapper.writeValueAsString(result);
            if (json.length() <= MAX_JSON_CHARS)
                redis.opsForValue().set(key, json, result.items().isEmpty() ? EMPTY_TTL : TTL);
        } catch (Exception ex) {
            log.debug("KAPT search cache write failed: type={}", ex.getClass().getSimpleName());
        }
    }
}
