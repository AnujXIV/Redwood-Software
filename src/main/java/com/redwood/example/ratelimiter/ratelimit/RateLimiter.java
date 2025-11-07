package com.redwood.example.ratelimiter.ratelimit;

/** Common interface so different limiter implementations can be swapped easily. */
public interface RateLimiter {
    boolean tryAcquire();
    boolean tryAcquire(int permits);
    long remaining();
    long capacity();
    double refillPerSecond();
}
