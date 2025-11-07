package com.redwood.example.ratelimiter.service;

import com.redwood.example.ratelimiter.config.LimitModels.RateLimitEntry;
import com.redwood.example.ratelimiter.config.LimitModels.RateLimitFile;
import com.redwood.example.ratelimiter.ratelimit.RateLimiter;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RateLimiterRegistryTest {

    private RateLimitFile sampleConfig() {
        RateLimitEntry global = new RateLimitEntry();
        global.endpoint = "*";
        global.refillRate = 10;
        global.bucketSize = 10;

        RateLimitEntry users = new RateLimitEntry();
        users.endpoint = "/api/v1/users";
        users.refillRate = 1;
        users.bucketSize = 3;

        RateLimitEntry logs = new RateLimitEntry();
        logs.endpoint = "/api/v2/logs";
        logs.refillRate = 2;
        logs.bucketSize = 6;

        RateLimitFile file = new RateLimitFile();
        file.limits = List.of(global, users, logs);
        return file;
    }

    @Test
    void resolvesConfigured_andFallsBackToGlobal() {
        RateLimiterRegistry reg = new RateLimiterRegistry(sampleConfig());

        RateLimiter users = reg.getForPath("/api/v1/users");
        RateLimiter logs = reg.getForPath("/api/v2/logs");
        RateLimiter global = reg.getForPath("/unknown");

        assertNotNull(users);
        assertNotNull(logs);
        assertNotNull(global);

        assertTrue(users != global, "Users limiter should be distinct from global");
        assertTrue(logs != global, "Logs limiter should be distinct from global");
    }

    @Test
    void globalBucketSharedAcrossUnconfiguredEndpoints() {
        RateLimiterRegistry reg = new RateLimiterRegistry(sampleConfig());
        RateLimiter g1 = reg.getForPath("/x");
        RateLimiter g2 = reg.getForPath("/y");

        assertSame(g1, g2, "Global limiter should be shared");

        int count = 0;
        while (g1.tryAcquire()) count++;

        assertTrue(count <= 10, "Should not allow more than capacity (10)");
        assertEquals(g1.remaining(), g2.remaining(), "Shared state should match");
    }
}