package com.redwood.example.ratelimiter.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import static com.redwood.example.ratelimiter.config.LimitModels.RateLimitFile;


/**
 * Loads the JSON rate-limit configuration either from:
 *  - classpath resource (e.g.config.json)
 *  - or directly from a file system path.
 */
public final class ConfigLoader {

    private final ObjectMapper mapper = JsonMapper.builder().build();

    public RateLimitFile loadFromClasspathOrFile(String resourceOrPath) throws IOException {
        // Try reading from classpath first
        InputStream cp = Thread.currentThread().getContextClassLoader().getResourceAsStream(resourceOrPath);
        if (cp != null) {
            try (cp) {
                return mapper.readValue(cp, RateLimitFile.class);
            }
        }

        // Fallback to file system path
        try(InputStream fs = Files.newInputStream(Paths.get(resourceOrPath))) {
            return mapper.readValue(fs, RateLimitFile.class);
        }
    }
}
