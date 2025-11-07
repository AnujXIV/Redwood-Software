/*
 * (C) Copyright 2019-2025 Redwood Technology B.V., Houten, The Netherlands
 */

package com.redwood.example.ratelimiter;

import com.redwood.example.ratelimiter.config.ConfigLoader;
import com.redwood.example.ratelimiter.config.LimitModels.RateLimitFile;
import com.redwood.example.ratelimiter.ratelimit.RateLimiter;
import com.redwood.example.ratelimiter.service.RateLimiterRegistry;
import jakarta.annotation.PostConstruct;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.NonNull;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;


/**
 * REST API exposing the rate-limit checking endpoints.
 */
record Greeting(String message) {}
record LimitResponse(@NonNull String message, boolean allowed, long requestsRemaining) {}

@RestController
@RequestMapping("/api")
public class ApiController {

    private RateLimiterRegistry registry;

    /** Initialize registry by loading JSON config once at startup. */
    @PostConstruct
    public void init() throws IOException {
        RateLimitFile config = new ConfigLoader().loadFromClasspathOrFile("config.json");
        this.registry = new RateLimiterRegistry(config);
    }

    @GetMapping("/")
    public Greeting root() {
        return new Greeting("Service is up and running");
    }

    /** Endpoint to check if a given path request is allowed under rate limits. */
    @GetMapping("/check")
    public ResponseEntity<LimitResponse> check(@RequestParam(name = "path", required = false) String path) {
        RateLimiter limiter = registry.getForPath(path);
        boolean allowed = limiter.tryAcquire();
        long remaining = limiter.remaining();

        LimitResponse body = new LimitResponse(allowed ? "Allowed" : "Rate limit exceeded", allowed, remaining);
        return allowed ? ResponseEntity.ok(body) : ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(body);
    }
}