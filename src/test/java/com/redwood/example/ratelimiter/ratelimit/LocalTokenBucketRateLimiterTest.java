package com.redwood.example.ratelimiter.ratelimit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for LocalTokenBucketRateLimiter.
 * Uses a deterministic FakeClock instead of real time to ensure
 * instant and reliable test execution.
 */
class LocalTokenBucketRateLimiterTest {

    /** Simulated clock for time control inside tests. */
    static final class FakeClock implements LocalTokenBucketRateLimiter.NanoClock {
        private long nowNanos;

        FakeClock(long startMillis) {
            this.nowNanos = TimeUnit.MILLISECONDS.toNanos(startMillis);
        }

        @Override
        public long nanoTime() {
            return nowNanos;
        }

        /** Move clock forward by a given duration in milliseconds. */
        void advanceMs(long ms) {
            nowNanos += TimeUnit.MILLISECONDS.toNanos(ms);
        }
    }

    private FakeClock clock;
    private LocalTokenBucketRateLimiter limiter;

    @BeforeEach
    void setup() {
        clock = new FakeClock(0);
        // Bucket of 5 tokens, refills at 2 tokens per second
        limiter = new LocalTokenBucketRateLimiter(
                LocalTokenBucketRateLimiter.Config.newBuilder()
                        .capacity(5)
                        .refillTokensPerSecond(2.0)
                        .clock(clock)
                        .build()
        );
    }

    /** Verify starting state and empty bucket behavior. */
    @Test
    void startsFull_andNeverNegative() {
        assertEquals(5, limiter.remaining(), "Should start full");

        for (int i = 0; i < 5; i++) {
            assertTrue(limiter.tryAcquire(), "Token " + i + " should be granted");
        }

        // Once depleted, no further acquisitions allowed
        assertFalse(limiter.tryAcquire(), "Should block when empty");
        assertEquals(0, limiter.remaining(), "Remaining tokens never negative");
    }

    /** Validate smooth refill over time, partial token restoration, and max cap. */
    @Test
    void refillsGraduallyOverTime() {
        // Drain completely
        for (int i = 0; i < 5; i++) limiter.tryAcquire();
        assertEquals(0, limiter.remaining(), "Empty after full usage");

        // 500ms → +1 token (2 tokens/sec)
        clock.advanceMs(500);
        assertTrue(limiter.tryAcquire(), "Should allow after 0.5s");
        assertEquals(0, limiter.remaining(), "1 token consumed");

        // 500ms more → +1 token again
        clock.advanceMs(500);
        assertTrue(limiter.tryAcquire(), "Should allow after another 0.5s");
        assertEquals(0, limiter.remaining(), "Bucket empty again");

        // 2.5s later → 5 tokens max (2/sec * 2.5s = 5)
        clock.advanceMs(2500);
        assertEquals(5, limiter.remaining(), "Should refill to full capacity only");
    }

    /** Check acquiring multiple permits at once and rejecting oversize requests. */
    @Test
    void multiplePermitRequestBehavior() {
        limiter = new LocalTokenBucketRateLimiter(
                LocalTokenBucketRateLimiter.Config.newBuilder()
                        .capacity(10)
                        .refillTokensPerSecond(5.0)
                        .clock(clock)
                        .build()
        );

        assertTrue(limiter.tryAcquire(4), "Should acquire 4 permits");
        assertEquals(6, limiter.remaining(), "6 remaining after acquiring 4");

        // Oversized request should fail and leave bucket unchanged
        assertFalse(limiter.tryAcquire(20), "Should reject requests exceeding capacity");
        assertEquals(6, limiter.remaining(), "Remaining unchanged after failed request");
    }

    /** Ensures bucket never exceeds capacity after long idle times. */
    @Test
    void doesNotExceedCapacityOnLongIdlePeriod() {
        // Drain completely
        for (int i = 0; i < 5; i++) limiter.tryAcquire();
        assertEquals(0, limiter.remaining());

        // Advance clock far into future → refill many times over
        clock.advanceMs(60_000);

        // Should cap at max (5)
        assertEquals(5, limiter.remaining(), "Never exceed max capacity");
    }

    /** Validate linear proportional refill based on elapsed time. */
    @Test
    void refillIsLinearWithTime() {
        limiter = new LocalTokenBucketRateLimiter(
                LocalTokenBucketRateLimiter.Config.newBuilder()
                        .capacity(10)
                        .refillTokensPerSecond(10.0) // 10 tokens per second
                        .clock(clock)
                        .build()
        );

        // Drain everything
        for (int i = 0; i < 10; i++) limiter.tryAcquire();
        assertEquals(0, limiter.remaining());

        // 0.1s elapsed → expect 1 token
        clock.advanceMs(100);
        assertEquals(1, limiter.remaining(), "0.1s -> 1 token expected");

        // 0.2s more → expect 3 total
        clock.advanceMs(200);
        assertEquals(3, limiter.remaining(), "0.3s -> 3 tokens total");

        // 0.7s more → capped at full (10)
        clock.advanceMs(700);
        assertEquals(10, limiter.remaining(), "Should cap at full capacity");
    }
}
