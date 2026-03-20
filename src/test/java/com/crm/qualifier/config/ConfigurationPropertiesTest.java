package com.crm.qualifier.config;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(args = {})
@Tag("integration")
class ConfigurationPropertiesTest {

    @Value("${cache.file-path}")
    private String cacheFilePath;

    @Value("${cache.ttl-hours}")
    private long ttlHours;

    @Value("${pipeline.score-threshold}")
    private int scoreThreshold;

    @Value("${pipeline.parallel-timeout-seconds}")
    private int parallelTimeout;

    @Autowired
    private Environment env;

    @Test
    void shouldLoadCachePath() {
        assertNotNull(cacheFilePath);
        assertTrue(cacheFilePath.contains("test-compliance-cache.json"),
                "Test profile should use test cache path, got: " + cacheFilePath);
    }

    @Test
    void shouldLoadTtl() {
        assertEquals(1, ttlHours, "Test TTL should be 1 hour");
    }

    @Test
    void shouldLoadPipelineDefaults() {
        assertEquals(60, scoreThreshold);
        assertEquals(5, parallelTimeout);
    }

    @Test
    void shouldLoadAdapterSeeds() {
        assertEquals("12345", env.getProperty("adapter.registry.seed"));
        assertEquals("67890", env.getProperty("adapter.judicial.seed"));
        assertEquals("11111", env.getProperty("adapter.compliance.seed"));
        assertEquals("99999", env.getProperty("adapter.score.seed"));
    }
}
