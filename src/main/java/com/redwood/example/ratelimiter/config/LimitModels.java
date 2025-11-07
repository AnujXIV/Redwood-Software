package com.redwood.example.ratelimiter.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/** Holds POJOs for deserializing config.json rate-limit settings. */
public final class LimitModels {

    /** A single rate-limit rule for one endpoint. */
    public static final class RateLimitEntry {

        @JsonProperty("endpoint")
        public String endpoint;

        @JsonProperty("refill-rate")
        public double refillRate;

        @JsonProperty("bucket-size")
        public long bucketSize;

        @Override
        public String toString() {
            return "RateLimitEntry{endpoint= '%s', bucketSize= %d, refillRate= %.2f}".formatted(endpoint, bucketSize, refillRate);
        }
    }

    /** Wrapper for the whole JSON structure: list of rate-limit entries. */
    public static final class RateLimitFile {
        @JsonProperty("limits")
        public List<RateLimitEntry> limits;
    }
}
