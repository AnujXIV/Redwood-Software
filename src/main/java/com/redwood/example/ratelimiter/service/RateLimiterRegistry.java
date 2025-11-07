package com.redwood.example.ratelimiter.service;

import com.redwood.example.ratelimiter.config.LimitModels.RateLimitEntry;
import com.redwood.example.ratelimiter.config.LimitModels.RateLimitFile;
import com.redwood.example.ratelimiter.ratelimit.LocalTokenBucketRateLimiter;
import com.redwood.example.ratelimiter.ratelimit.RateLimiter;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public final class RateLimiterRegistry {

    private static final String GLOBAL = "*";
    private final Map<String, RateLimiter> buckets = new ConcurrentHashMap<>();
    private final RateLimiter global;

    /**
     * Registry mapping each API endpoint to its corresponding RateLimiter.
     * Falls back to a shared global limiter for unconfigured paths.
     */
    public RateLimiterRegistry(RateLimitFile file) {
        Objects.requireNonNull(file);

        // Locate the global entry (mandatory)
        RateLimitEntry globalEntry = file.limits.stream().filter(l -> GLOBAL.equals(l.endpoint)).findFirst().orElseThrow(()-> new IllegalArgumentException("Global '*' limit required "));

        this.global = build(globalEntry);

        // Create per-endpoint buckets
        for(RateLimitEntry e : file.limits) {
            if(!GLOBAL.equals(e.endpoint)) buckets.put(e.endpoint, build(e));
        }
    }

    /** Factory method to construct a limiter from configuration. */
    private static RateLimiter build(RateLimitEntry e){
        return new LocalTokenBucketRateLimiter(
                LocalTokenBucketRateLimiter.Config.newBuilder().capacity(e.bucketSize).refillTokensPerSecond(e.refillRate).build()
        );
    }

    /** Returns the limiter for a given path or the global fallback. */
    public RateLimiter getForPath(String path){
        if(path == null || path.isBlank()) return global;
        return buckets.getOrDefault(path, global);
    }
}
