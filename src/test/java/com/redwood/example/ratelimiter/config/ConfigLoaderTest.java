package com.redwood.example.ratelimiter.config;

import com.redwood.example.ratelimiter.config.LimitModels.RateLimitFile;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies that ConfigLoader can successfully load and parse the built-in config.json file.
 */
class ConfigLoaderTest {

    @Test
    void loadsClasspathConfigSuccessfully() throws Exception {
        ConfigLoader loader = new ConfigLoader();

        // Attempt to load the default classpath config
        RateLimitFile file = loader.loadFromClasspathOrFile("config.json");

        // Basic structure sanity checks
        assertNotNull(file, "Config file should be loaded");
        assertNotNull(file.limits, "Limits list should not be null");
        assertFalse(file.limits.isEmpty(), "Should contain rate limit entries");

        // Verify there is at least one global rule ('*')
        boolean hasGlobal = file.limits.stream()
                .anyMatch(l -> "*".equals(l.endpoint));

        assertTrue(hasGlobal, "Should have a global '*' entry");
    }
}