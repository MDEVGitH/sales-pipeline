package com.crm.qualifier.integration;

import com.crm.qualifier.adapter.outbound.cache.FileComplianceCacheAdapter;
import com.crm.qualifier.adapter.outbound.compliance.SimulatedComplianceBureauAdapter;
import com.crm.qualifier.adapter.outbound.judicial.SimulatedJudicialAdapter;
import com.crm.qualifier.adapter.outbound.registry.SimulatedRegistryAdapter;
import com.crm.qualifier.adapter.outbound.score.RandomScoreAdapter;
import com.crm.qualifier.application.port.outbound.ComplianceBureauPort;
import com.crm.qualifier.application.service.LeadQualificationService;
import com.crm.qualifier.domain.exception.ComplianceBureauUnavailableException;
import com.crm.qualifier.domain.model.ComplianceCheckResult;
import com.crm.qualifier.domain.model.ComplianceCheckResult.ComplianceStatus;
import com.crm.qualifier.domain.model.Lead;
import com.crm.qualifier.domain.model.PipelineResult;
import com.crm.qualifier.domain.model.QualificationStatus;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

@Tag("integration")
class ComplianceCacheIntegrationTest {

    @TempDir
    Path tempDir;

    private static final Lead VALID_LEAD = new Lead(
            "ABC123456", LocalDate.of(1990, 1, 15), "John", "Doe", "john@example.com"
    );

    @Test
    void shouldCacheComplianceResultOnFirstCall() {
        String cachePath = tempDir.resolve("cache.json").toString();

        // Find seeds that pass parallel phase and hit compliance
        for (long baseSeed = 0; baseSeed < 100; baseSeed++) {
            var registryPort = new SimulatedRegistryAdapter(baseSeed);
            var judicialPort = new SimulatedJudicialAdapter(baseSeed);
            var compliancePort = new SimulatedComplianceBureauAdapter(baseSeed + 1000);
            var scorePort = new RandomScoreAdapter(baseSeed + 2000);
            var cachePort = new FileComplianceCacheAdapter(cachePath, 24);

            var service = new LeadQualificationService(
                    registryPort, judicialPort, compliancePort, scorePort, cachePort);

            PipelineResult result = service.qualify(VALID_LEAD);

            if (result.status() != QualificationStatus.MANUAL_REVIEW
                    && result.validationResults().size() >= 3) {
                // Compliance was called, cache file should exist
                assertTrue(Files.exists(Path.of(cachePath)), "Cache file should exist after compliance call");
                return;
            }
        }
        fail("Could not find seeds that reach compliance phase");
    }

    @Test
    void shouldServeCachedResultOnSecondCall() {
        String cachePath = tempDir.resolve("cache-serve.json").toString();

        // First, pre-populate cache
        var cacheAdapter = new FileComplianceCacheAdapter(cachePath, 24);
        cacheAdapter.put(VALID_LEAD.nationalId(), new ComplianceCheckResult(ComplianceStatus.CLEAR));

        // Now use a bureau that always throws — but cache should save us
        ComplianceBureauPort alwaysThrows = nationalId -> {
            throw new ComplianceBureauUnavailableException("Always down");
        };

        // Find seeds where parallel passes
        for (long baseSeed = 0; baseSeed < 100; baseSeed++) {
            var registryPort = new SimulatedRegistryAdapter(baseSeed);
            var judicialPort = new SimulatedJudicialAdapter(baseSeed);
            var scorePort = new RandomScoreAdapter(baseSeed + 2000);
            var cachePort = new FileComplianceCacheAdapter(cachePath, 24);

            var service = new LeadQualificationService(
                    registryPort, judicialPort, alwaysThrows, scorePort, cachePort);

            PipelineResult result = service.qualify(VALID_LEAD);

            // If parallel passed, compliance should come from cache (CLEAR), not throw
            if (result.validationResults().size() >= 3) {
                assertNotEquals(QualificationStatus.MANUAL_REVIEW, result.status(),
                        "Should use cached CLEAR result, not trigger MANUAL_REVIEW");
                return;
            }
        }
        fail("Could not find seeds where parallel passes");
    }

    @Test
    void shouldExpireCacheEntries() throws Exception {
        String cachePath = tempDir.resolve("cache-expire.json").toString();

        // Use TTL of 0 hours so entries expire immediately
        var cacheAdapter = new FileComplianceCacheAdapter(cachePath, 0);
        cacheAdapter.put(VALID_LEAD.nationalId(), new ComplianceCheckResult(ComplianceStatus.CLEAR));

        Thread.sleep(10); // Ensure time passes

        // Entry should be expired
        assertTrue(cacheAdapter.get(VALID_LEAD.nationalId()).isEmpty(),
                "Cache entry should be expired with TTL=0");
    }
}
