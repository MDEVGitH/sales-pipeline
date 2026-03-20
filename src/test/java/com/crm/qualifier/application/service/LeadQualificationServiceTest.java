package com.crm.qualifier.application.service;

import com.crm.qualifier.application.port.outbound.*;
import com.crm.qualifier.domain.exception.ComplianceBureauUnavailableException;
import com.crm.qualifier.domain.model.*;
import com.crm.qualifier.domain.model.ComplianceCheckResult.ComplianceStatus;
import com.crm.qualifier.domain.model.JudicialCheckResult.JudicialStatus;
import com.crm.qualifier.domain.model.RegistryCheckResult.RegistryStatus;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
class LeadQualificationServiceTest {

    private static final Lead VALID_LEAD = new Lead(
            "ABC123456", LocalDate.of(1990, 1, 15), "John", "Doe", "john@example.com"
    );

    // ── Stub factories ──

    private static RegistryPort registryReturning(RegistryStatus status) {
        return lead -> new RegistryCheckResult(status,
                status == RegistryStatus.MATCH ? "Matched" :
                        status == RegistryStatus.NOT_FOUND ? "Not found" : "Mismatched data");
    }

    private static JudicialPort judicialReturning(JudicialStatus status) {
        return nationalId -> new JudicialCheckResult(status);
    }

    private static ComplianceBureauPort complianceReturning(ComplianceStatus status) {
        return nationalId -> new ComplianceCheckResult(status);
    }

    private static ComplianceBureauPort complianceUnavailable() {
        return nationalId -> {
            throw new ComplianceBureauUnavailableException("Service unavailable");
        };
    }

    private static QualificationScorePort scoreReturning(int score) {
        return () -> score;
    }

    private static ComplianceCachePort noCache() {
        return new ComplianceCachePort() {
            public Optional<ComplianceCheckResult> get(String id) { return Optional.empty(); }
            public void put(String id, ComplianceCheckResult r) { /* no-op */ }
        };
    }

    private static ComplianceCachePort cacheReturning(ComplianceCheckResult result) {
        return new ComplianceCachePort() {
            public Optional<ComplianceCheckResult> get(String id) { return Optional.of(result); }
            public void put(String id, ComplianceCheckResult r) { /* no-op */ }
        };
    }

    private LeadQualificationService buildService(
            RegistryPort registry, JudicialPort judicial,
            ComplianceBureauPort compliance, QualificationScorePort score,
            ComplianceCachePort cache) {
        return new LeadQualificationService(registry, judicial, compliance, score, cache);
    }

    // ── Happy Path ──

    @Test
    void shouldApprove_whenAllValidationsPass() {
        var service = buildService(
                registryReturning(RegistryStatus.MATCH),
                judicialReturning(JudicialStatus.CLEAN),
                complianceReturning(ComplianceStatus.CLEAR),
                scoreReturning(75),
                noCache()
        );

        PipelineResult result = service.qualify(VALID_LEAD);
        assertEquals(QualificationStatus.APPROVED, result.status());
        assertTrue(result.getProspect().isPresent());
        assertEquals(4, result.validationResults().size());
    }

    @Test
    void shouldApprove_withBoundaryScore61() {
        var service = buildService(
                registryReturning(RegistryStatus.MATCH),
                judicialReturning(JudicialStatus.CLEAN),
                complianceReturning(ComplianceStatus.CLEAR),
                scoreReturning(61),
                noCache()
        );

        PipelineResult result = service.qualify(VALID_LEAD);
        assertEquals(QualificationStatus.APPROVED, result.status());
    }

    // ── Registry Failures ──

    @Test
    void shouldReject_whenRegistryReturnsMismatch() {
        var service = buildService(
                registryReturning(RegistryStatus.MISMATCH),
                judicialReturning(JudicialStatus.CLEAN),
                complianceReturning(ComplianceStatus.CLEAR),
                scoreReturning(75),
                noCache()
        );

        PipelineResult result = service.qualify(VALID_LEAD);
        assertEquals(QualificationStatus.REJECTED, result.status());
        assertEquals(2, result.validationResults().size());
    }

    @Test
    void shouldReject_whenRegistryReturnsNotFound() {
        var service = buildService(
                registryReturning(RegistryStatus.NOT_FOUND),
                judicialReturning(JudicialStatus.CLEAN),
                complianceReturning(ComplianceStatus.CLEAR),
                scoreReturning(75),
                noCache()
        );

        PipelineResult result = service.qualify(VALID_LEAD);
        assertEquals(QualificationStatus.REJECTED, result.status());
        assertEquals(2, result.validationResults().size());
    }

    // ── Judicial Failures ──

    @Test
    void shouldReject_whenJudicialReturnsHasRecords() {
        var service = buildService(
                registryReturning(RegistryStatus.MATCH),
                judicialReturning(JudicialStatus.HAS_RECORDS),
                complianceReturning(ComplianceStatus.CLEAR),
                scoreReturning(75),
                noCache()
        );

        PipelineResult result = service.qualify(VALID_LEAD);
        assertEquals(QualificationStatus.REJECTED, result.status());
        assertEquals(2, result.validationResults().size());
    }

    // ── Both Parallel Steps Fail ──

    @Test
    void shouldReject_whenBothParallelStepsFail() {
        var service = buildService(
                registryReturning(RegistryStatus.NOT_FOUND),
                judicialReturning(JudicialStatus.HAS_RECORDS),
                complianceReturning(ComplianceStatus.CLEAR),
                scoreReturning(75),
                noCache()
        );

        PipelineResult result = service.qualify(VALID_LEAD);
        assertEquals(QualificationStatus.REJECTED, result.status());
        assertEquals(2, result.validationResults().size());
        // Both failures should be reported
        long failures = result.validationResults().stream().filter(r -> !r.success()).count();
        assertEquals(2, failures);
    }

    // ── Compliance Failures ──

    @Test
    void shouldReject_whenComplianceReturnsFlagged() {
        var service = buildService(
                registryReturning(RegistryStatus.MATCH),
                judicialReturning(JudicialStatus.CLEAN),
                complianceReturning(ComplianceStatus.FLAGGED),
                scoreReturning(75),
                noCache()
        );

        PipelineResult result = service.qualify(VALID_LEAD);
        assertEquals(QualificationStatus.REJECTED, result.status());
        assertEquals(3, result.validationResults().size());
    }

    @Test
    void shouldReturnManualReview_whenComplianceServiceUnavailable() {
        var service = buildService(
                registryReturning(RegistryStatus.MATCH),
                judicialReturning(JudicialStatus.CLEAN),
                complianceUnavailable(),
                scoreReturning(75),
                noCache()
        );

        PipelineResult result = service.qualify(VALID_LEAD);
        assertEquals(QualificationStatus.MANUAL_REVIEW, result.status());
        assertEquals(2, result.validationResults().size());
    }

    // ── Score Failures ──

    @Test
    void shouldReject_whenScoreEquals60() {
        var service = buildService(
                registryReturning(RegistryStatus.MATCH),
                judicialReturning(JudicialStatus.CLEAN),
                complianceReturning(ComplianceStatus.CLEAR),
                scoreReturning(60),
                noCache()
        );

        PipelineResult result = service.qualify(VALID_LEAD);
        assertEquals(QualificationStatus.REJECTED, result.status());
        assertEquals(4, result.validationResults().size());
    }

    @Test
    void shouldReject_whenScoreEquals0() {
        var service = buildService(
                registryReturning(RegistryStatus.MATCH),
                judicialReturning(JudicialStatus.CLEAN),
                complianceReturning(ComplianceStatus.CLEAR),
                scoreReturning(0),
                noCache()
        );

        PipelineResult result = service.qualify(VALID_LEAD);
        assertEquals(QualificationStatus.REJECTED, result.status());
        assertEquals(4, result.validationResults().size());
    }

    // ── Short-Circuit Verification ──

    @Test
    void shouldNotCallCompliance_whenParallelStepsFail() {
        AtomicBoolean complianceCalled = new AtomicBoolean(false);
        ComplianceBureauPort trackingCompliance = nationalId -> {
            complianceCalled.set(true);
            return new ComplianceCheckResult(ComplianceStatus.CLEAR);
        };

        var service = buildService(
                registryReturning(RegistryStatus.NOT_FOUND),
                judicialReturning(JudicialStatus.CLEAN),
                trackingCompliance,
                scoreReturning(75),
                noCache()
        );

        service.qualify(VALID_LEAD);
        assertFalse(complianceCalled.get(), "Compliance should not be called when parallel steps fail");
    }

    @Test
    void shouldNotCallScore_whenComplianceFails() {
        AtomicBoolean scoreCalled = new AtomicBoolean(false);
        QualificationScorePort trackingScore = () -> {
            scoreCalled.set(true);
            return 75;
        };

        var service = buildService(
                registryReturning(RegistryStatus.MATCH),
                judicialReturning(JudicialStatus.CLEAN),
                complianceReturning(ComplianceStatus.FLAGGED),
                trackingScore,
                noCache()
        );

        service.qualify(VALID_LEAD);
        assertFalse(scoreCalled.get(), "Score should not be called when compliance fails");
    }

    @Test
    void shouldNotCallScore_whenComplianceUnavailable() {
        AtomicBoolean scoreCalled = new AtomicBoolean(false);
        QualificationScorePort trackingScore = () -> {
            scoreCalled.set(true);
            return 75;
        };

        var service = buildService(
                registryReturning(RegistryStatus.MATCH),
                judicialReturning(JudicialStatus.CLEAN),
                complianceUnavailable(),
                trackingScore,
                noCache()
        );

        service.qualify(VALID_LEAD);
        assertFalse(scoreCalled.get(), "Score should not be called when compliance is unavailable");
    }

    // ── Cache Interaction ──

    @Test
    void shouldUseCache_whenCacheHit() {
        AtomicBoolean bureauCalled = new AtomicBoolean(false);
        ComplianceBureauPort trackingBureau = nationalId -> {
            bureauCalled.set(true);
            return new ComplianceCheckResult(ComplianceStatus.CLEAR);
        };

        var service = buildService(
                registryReturning(RegistryStatus.MATCH),
                judicialReturning(JudicialStatus.CLEAN),
                trackingBureau,
                scoreReturning(75),
                cacheReturning(new ComplianceCheckResult(ComplianceStatus.CLEAR))
        );

        PipelineResult result = service.qualify(VALID_LEAD);
        assertFalse(bureauCalled.get(), "Bureau should not be called when cache hits");
        assertEquals(QualificationStatus.APPROVED, result.status());
    }

    @Test
    void shouldCallBureauAndCache_whenCacheMiss() {
        AtomicBoolean bureauCalled = new AtomicBoolean(false);
        AtomicBoolean cachePutCalled = new AtomicBoolean(false);

        ComplianceBureauPort trackingBureau = nationalId -> {
            bureauCalled.set(true);
            return new ComplianceCheckResult(ComplianceStatus.CLEAR);
        };

        ComplianceCachePort trackingCache = new ComplianceCachePort() {
            public Optional<ComplianceCheckResult> get(String id) { return Optional.empty(); }
            public void put(String id, ComplianceCheckResult r) {
                cachePutCalled.set(true);
                assertEquals(ComplianceStatus.CLEAR, r.status());
            }
        };

        var service = buildService(
                registryReturning(RegistryStatus.MATCH),
                judicialReturning(JudicialStatus.CLEAN),
                trackingBureau,
                scoreReturning(75),
                trackingCache
        );

        service.qualify(VALID_LEAD);
        assertTrue(bureauCalled.get(), "Bureau should be called on cache miss");
        assertTrue(cachePutCalled.get(), "Cache put should be called after bureau success");
    }

    @Test
    void shouldNotCacheError_whenBureauUnavailable() {
        AtomicBoolean cachePutCalled = new AtomicBoolean(false);

        ComplianceCachePort trackingCache = new ComplianceCachePort() {
            public Optional<ComplianceCheckResult> get(String id) { return Optional.empty(); }
            public void put(String id, ComplianceCheckResult r) { cachePutCalled.set(true); }
        };

        var service = buildService(
                registryReturning(RegistryStatus.MATCH),
                judicialReturning(JudicialStatus.CLEAN),
                complianceUnavailable(),
                scoreReturning(75),
                trackingCache
        );

        service.qualify(VALID_LEAD);
        assertFalse(cachePutCalled.get(), "Cache put should not be called when bureau is unavailable");
    }

    // ── Parallel Execution Verification ──

    @Test
    void shouldExecuteRegistryAndJudicialInParallel() {
        RegistryPort slowRegistry = lead -> {
            try { Thread.sleep(500); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            return new RegistryCheckResult(RegistryStatus.MATCH, "Matched");
        };

        JudicialPort slowJudicial = nationalId -> {
            try { Thread.sleep(500); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            return new JudicialCheckResult(JudicialStatus.CLEAN);
        };

        var service = buildService(
                slowRegistry,
                slowJudicial,
                complianceReturning(ComplianceStatus.CLEAR),
                scoreReturning(75),
                noCache()
        );

        long start = System.currentTimeMillis();
        PipelineResult result = service.qualify(VALID_LEAD);
        long elapsed = System.currentTimeMillis() - start;

        assertEquals(QualificationStatus.APPROVED, result.status());
        assertTrue(elapsed < 800, "Parallel execution should complete in < 800ms, was " + elapsed + "ms");
    }
}
