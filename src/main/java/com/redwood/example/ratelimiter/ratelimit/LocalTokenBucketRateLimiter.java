package com.redwood.example.ratelimiter.ratelimit;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;


/**
 * Thread-safe Token Bucket Rate Limiter.
 * - Refill happens continuously based on elapsed nanoseconds.
 * - Allows fractional tokens for smooth refill timing.
 * - CAS (compare-and-set) ensures safe updates without full synchronization.
 */
public class LocalTokenBucketRateLimiter implements RateLimiter {

    /** Clock abstraction so tests can use a fake clock. */
    public interface NanoClock {
        long nanoTime();
        static NanoClock system() { return System::nanoTime; }
    }

    /** Configuration holder built via builder pattern. */
    public static final class Config {
        private final long capacity;
        private final double refillPerSecond;
        private final NanoClock clock;

        private Config(long capacity, double refillPerSecond, NanoClock clock) {
            if (capacity <= 0 || refillPerSecond <= 0)
                throw new IllegalArgumentException("Capacity and rate must be positive");
            this.capacity = capacity;
            this.refillPerSecond = refillPerSecond;
            this.clock = Objects.requireNonNull(clock);
        }

        public static Builder newBuilder() { return new Builder(); }

        /** Fluent builder for readability. */
        public static final class Builder {
            private long capacity;
            private double refillPerSecond;
            private NanoClock clock = NanoClock.system();
            public Builder capacity(long c) { this.capacity = c; return this; }
            public Builder refillTokensPerSecond(double r) { this.refillPerSecond = r; return this; }
            public Builder clock(NanoClock c) { this.clock = c; return this; }
            public Config build() { return new Config(capacity, refillPerSecond, clock); }
        }
    }

    /** Immutable state holder for current bucket tokens. */
    private static final class State {
        final double available;
        final long lastRefillNs;
        State(double available, long lastRefillNs) {
            this.available = available;
            this.lastRefillNs = lastRefillNs;
        }
    }

    private final long capacity;
    private final double refillPerNs;
    private final NanoClock clock;
    private final AtomicReference<State> state;

    public LocalTokenBucketRateLimiter(Config cfg) {
        this.capacity = cfg.capacity;
        this.refillPerNs = cfg.refillPerSecond / 1_000_000_000d;
        this.clock = cfg.clock;
        this.state = new AtomicReference<>(new State(capacity, clock.nanoTime()));
    }

    @Override
    public boolean tryAcquire() { return tryAcquire(1); }

    /**
     * Attempts to acquire N tokens.
     * Returns true if enough tokens are available, false otherwise.
     */
    @Override
    public boolean tryAcquire(int permits) {
        int attempts = 0;
        final double EPS = 1e-5;

        while (attempts < 1000) {
            long now = clock.nanoTime();
            State current = state.get();

            // Compute tokens available since last refill
            State refilled = refill(current, now);
            if (refilled != current) state.compareAndSet(current, refilled);

            if (refilled.available + EPS >= permits) {
                State updated = new State(refilled.available - permits, refilled.lastRefillNs);
                if (state.compareAndSet(refilled, updated)) return true;
            } else {
                return false;
            }

            attempts++;
            if ((attempts & 0xFF) == 0) Thread.yield();
        }
        return false;
    }

    /** Adds tokens proportional to elapsed time since last refill. */
    private State refill(State prev, long now) {
        if (now <= prev.lastRefillNs) return prev;
        long elapsed = now - prev.lastRefillNs;
        double add = elapsed * refillPerNs;
        double newAvail = prev.available + add;
        if (newAvail > capacity) newAvail = capacity;
        return new State(newAvail, now);
    }

    @Override
    public long remaining() {
        final double EPS = 1e-5;
        State s = refill(state.get(), clock.nanoTime());
        state.set(s);
        return (long) Math.floor(s.available + EPS);
    }

    @Override
    public long capacity() { return capacity; }

    @Override
    public double refillPerSecond() { return refillPerNs * 1_000_000_000d; }
}
