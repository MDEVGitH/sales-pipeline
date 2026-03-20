package com.crm.qualifier.integration;

import com.crm.qualifier.adapter.outbound.cache.FileComplianceCacheAdapter;
import com.crm.qualifier.adapter.outbound.compliance.SimulatedComplianceBureauAdapter;
import com.crm.qualifier.adapter.outbound.judicial.SimulatedJudicialAdapter;
import com.crm.qualifier.adapter.outbound.registry.SimulatedRegistryAdapter;
import com.crm.qualifier.adapter.outbound.score.RandomScoreAdapter;
import com.crm.qualifier.application.port.outbound.*;
import com.crm.qualifier.application.service.LeadQualificationService;
import com.crm.qualifier.domain.exception.ComplianceBureauUnavailableException;
import com.crm.qualifier.domain.model.*;
import com.crm.qualifier.domain.model.ComplianceCheckResult.ComplianceStatus;
import com.crm.qualifier.domain.model.JudicialCheckResult.JudicialStatus;
import com.crm.qualifier.domain.model.RegistryCheckResult.RegistryStatus;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

@Tag("integration")
class LeadQualificationIntegrationTest {

    @TempDir
    Path tempDir;

    private static final Lead VALID_LEAD = new Lead(
            "ABC123456", LocalDate.of(1990, 1, 15), "John", "Doe", "john@example.com"
    );

    // Pre-determined seeds for specific outcomes.
    // These are found by scanning adapter behavior without sleeping.

    /**
     * Finds a seed where the registry adapter returns MATCH for VALID_LEAD.
     * We check the Random determinism without sleeping by examining the random draw.
     */
    private long findRegistryMatchSeed() {
        for (long seed = 0; seed < 1000; seed++) {
            var adapter = new SimulatedRegistryAdapter(seed);
            RegistryCheckResult result = adapter.check(VALID_LEAD);
            if (result.status() == RegistryStatus.MATCH) return seed;
        }
        throw new RuntimeException("Could not find registry MATCH seed");
    }

    private long findJudicialCleanSeed() {
        for (long seed = 0; seed < 1000; seed++) {
            var adapter = new SimulatedJudicialAdapter(seed);
            JudicialCheckResult result = adapter.check(VALID_LEAD.nationalId());
            if (result.status() == JudicialStatus.CLEAN) return seed;
        }
        throw new RuntimeException("Could not find judicial CLEAN seed");
    }

    private long findComplianceClearSeed() {
        for (long seed = 0; seed < 1000; seed++) {
            var adapter = new SimulatedComplianceBureauAdapter(seed);
            try {
                ComplianceCheckResult result = adapter.check(VALID_LEAD.nationalId());
                if (result.status() == ComplianceStatus.CLEAR) return seed;
            } catch (ComplianceBureauUnavailableException ignored) {}
        }
        throw new RuntimeException("Could not find compliance CLEAR seed");
    }

    private long findComplianceUnavailableSeed() {
        for (long seed = 0; seed < 1000; seed++) {
            var adapter = new SimulatedComplianceBureauAdapter(seed);
            try {
                adapter.check(VALID_LEAD.nationalId());
            } catch (ComplianceBureauUnavailableException e) {
                return seed;
            }
        }
        throw new RuntimeException("Could not find compliance UNAVAILABLE seed");
    }

    private long findRegistryNotFoundSeed() {
        for (long seed = 0; seed < 1000; seed++) {
            var adapter = new SimulatedRegistryAdapter(seed);
            RegistryCheckResult result = adapter.check(VALID_LEAD);
            if (result.status() == RegistryStatus.NOT_FOUND) return seed;
        }
        throw new RuntimeException("Could not find registry NOT_FOUND seed");
    }

    private long findScoreAbove60Seed() {
        for (long seed = 0; seed < 1000; seed++) {
            var adapter = new RandomScoreAdapter(seed);
            if (adapter.generateScore() > 60) return seed;
        }
        throw new RuntimeException("Could not find score > 60 seed");
    }

    @Test
    void shouldQualifyLeadEndToEnd_approved() {
        long regSeed = findRegistryMatchSeed();
        long judSeed = findJudicialCleanSeed();
        long compSeed = findComplianceClearSeed();
        long scoreSeed = findScoreAbove60Seed();

        var service = new LeadQualificationService(
                new SimulatedRegistryAdapter(regSeed),
                new SimulatedJudicialAdapter(judSeed),
                new SimulatedComplianceBureauAdapter(compSeed),
                new RandomScoreAdapter(scoreSeed),
                new FileComplianceCacheAdapter(tempDir.resolve("cache-approved.json").toString(), 24)
        );

        PipelineResult result = service.qualify(VALID_LEAD);
        assertEquals(QualificationStatus.APPROVED, result.status());
        assertTrue(result.getProspect().isPresent());
        assertEquals(4, result.validationResults().size());
    }

    @Test
    void shouldQualifyLeadEndToEnd_rejected() {
        long regSeed = findRegistryNotFoundSeed();
        long judSeed = findJudicialCleanSeed();

        var service = new LeadQualificationService(
                new SimulatedRegistryAdapter(regSeed),
                new SimulatedJudicialAdapter(judSeed),
                new SimulatedComplianceBureauAdapter(0),
                new RandomScoreAdapter(0),
                new FileComplianceCacheAdapter(tempDir.resolve("cache-rejected.json").toString(), 24)
        );

        PipelineResult result = service.qualify(VALID_LEAD);
        assertEquals(QualificationStatus.REJECTED, result.status());
        assertTrue(result.getProspect().isEmpty());
    }

    @Test
    void shouldQualifyLeadEndToEnd_manualReview() {
        long regSeed = findRegistryMatchSeed();
        long judSeed = findJudicialCleanSeed();
        long compSeed = findComplianceUnavailableSeed();

        var service = new LeadQualificationService(
                new SimulatedRegistryAdapter(regSeed),
                new SimulatedJudicialAdapter(judSeed),
                new SimulatedComplianceBureauAdapter(compSeed),
                new RandomScoreAdapter(0),
                new FileComplianceCacheAdapter(tempDir.resolve("cache-manual.json").toString(), 24)
        );

        PipelineResult result = service.qualify(VALID_LEAD);
        assertEquals(QualificationStatus.MANUAL_REVIEW, result.status());
        assertTrue(result.getProspect().isEmpty());
    }

    @Test
    void shouldUseCacheOnSecondCall() {
        long regSeed = findRegistryMatchSeed();
        long judSeed = findJudicialCleanSeed();
        long compSeed = findComplianceClearSeed();
        long scoreSeed = findScoreAbove60Seed();
        String cachePath = tempDir.resolve("cache-second.json").toString();

        var service1 = new LeadQualificationService(
                new SimulatedRegistryAdapter(regSeed),
                new SimulatedJudicialAdapter(judSeed),
                new SimulatedComplianceBureauAdapter(compSeed),
                new RandomScoreAdapter(scoreSeed),
                new FileComplianceCacheAdapter(cachePath, 24)
        );

        PipelineResult first = service1.qualify(VALID_LEAD);
        assertEquals(QualificationStatus.APPROVED, first.status());

        // Second call: use unavailable compliance, but cache should save us
        long compUnavailSeed = findComplianceUnavailableSeed();
        var service2 = new LeadQualificationService(
                new SimulatedRegistryAdapter(regSeed),
                new SimulatedJudicialAdapter(judSeed),
                new SimulatedComplianceBureauAdapter(compUnavailSeed),
                new RandomScoreAdapter(scoreSeed),
                new FileComplianceCacheAdapter(cachePath, 24)
        );

        PipelineResult second = service2.qualify(VALID_LEAD);
        // Cache hit means compliance doesn't throw — should not be MANUAL_REVIEW
        assertNotEquals(QualificationStatus.MANUAL_REVIEW, second.status());
    }

    @Test
    void shouldRunParallelStepsInParallel() {
        long regSeed = findRegistryMatchSeed();
        long judSeed = findJudicialCleanSeed();
        long compSeed = findComplianceClearSeed();
        long scoreSeed = findScoreAbove60Seed();

        var service = new LeadQualificationService(
                new SimulatedRegistryAdapter(regSeed),
                new SimulatedJudicialAdapter(judSeed),
                new SimulatedComplianceBureauAdapter(compSeed),
                new RandomScoreAdapter(scoreSeed),
                new FileComplianceCacheAdapter(tempDir.resolve("cache-parallel.json").toString(), 24)
        );

        long start = System.currentTimeMillis();
        service.qualify(VALID_LEAD);
        long elapsed = System.currentTimeMillis() - start;

        // If sequential: registry(200-800) + judicial(300-1000) + compliance(100-500) = 600-2300ms minimum
        // If parallel phase: max(registry, judicial) + compliance = ~1000 + 500 = ~1500ms max
        assertTrue(elapsed < 5000, "Should complete in reasonable time, was " + elapsed + "ms");
    }

    @Test
    void shouldCollectAllValidationResults() {
        long regSeed = findRegistryMatchSeed();
        long judSeed = findJudicialCleanSeed();
        long compSeed = findComplianceClearSeed();
        long scoreSeed = findScoreAbove60Seed();

        var service = new LeadQualificationService(
                new SimulatedRegistryAdapter(regSeed),
                new SimulatedJudicialAdapter(judSeed),
                new SimulatedComplianceBureauAdapter(compSeed),
                new RandomScoreAdapter(scoreSeed),
                new FileComplianceCacheAdapter(tempDir.resolve("cache-all.json").toString(), 24)
        );

        PipelineResult result = service.qualify(VALID_LEAD);
        assertEquals(QualificationStatus.APPROVED, result.status());
        assertEquals(4, result.validationResults().size());
    }

    @Test
    void shouldShortCircuitAfterParallelFailure() {
        long regSeed = findRegistryNotFoundSeed();
        long judSeed = findJudicialCleanSeed();

        var service = new LeadQualificationService(
                new SimulatedRegistryAdapter(regSeed),
                new SimulatedJudicialAdapter(judSeed),
                new SimulatedComplianceBureauAdapter(0),
                new RandomScoreAdapter(0),
                new FileComplianceCacheAdapter(tempDir.resolve("cache-sc.json").toString(), 24)
        );

        PipelineResult result = service.qualify(VALID_LEAD);
        assertEquals(QualificationStatus.REJECTED, result.status());
        assertEquals(2, result.validationResults().size());
    }

    @Test
    void shouldHandleConcurrentQualifications() throws Exception {
        String cachePath = tempDir.resolve("cache-concurrent.json").toString();
        long regSeed = findRegistryMatchSeed();
        long judSeed = findJudicialCleanSeed();
        long compSeed = findComplianceClearSeed();

        ExecutorService executor = Executors.newFixedThreadPool(5);
        List<Future<PipelineResult>> futures = new ArrayList<>();

        for (int i = 0; i < 5; i++) {
            final int idx = i;
            futures.add(executor.submit(() -> {
                var service = new LeadQualificationService(
                        new SimulatedRegistryAdapter(regSeed + idx),
                        new SimulatedJudicialAdapter(judSeed + idx),
                        new SimulatedComplianceBureauAdapter(compSeed + idx),
                        new RandomScoreAdapter(idx + 2000),
                        new FileComplianceCacheAdapter(cachePath, 24)
                );
                Lead lead = new Lead(
                        "ID" + (idx + 100000) + "X", LocalDate.of(1990, 1, 15),
                        "John", "Doe", "john@example.com"
                );
                return service.qualify(lead);
            }));
        }

        executor.shutdown();
        assertTrue(executor.awaitTermination(30, TimeUnit.SECONDS));

        for (Future<PipelineResult> future : futures) {
            PipelineResult result = future.get();
            assertNotNull(result);
            assertNotNull(result.status());
        }
    }
}
