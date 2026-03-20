package com.crm.qualifier.adapter.outbound.compliance;

import com.crm.qualifier.domain.exception.ComplianceBureauUnavailableException;
import com.crm.qualifier.domain.model.ComplianceCheckResult;
import com.crm.qualifier.domain.model.ComplianceCheckResult.ComplianceStatus;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
class SimulatedComplianceBureauAdapterTest {

    @Test
    void shouldReturnClearOrFlagged_whenServiceUp() {
        int successCount = 0;
        for (int i = 0; i < 100; i++) {
            SimulatedComplianceBureauAdapter adapter = new SimulatedComplianceBureauAdapter((long) i);
            try {
                ComplianceCheckResult result = adapter.check("ABC123456");
                assertTrue(Set.of(ComplianceStatus.CLEAR, ComplianceStatus.FLAGGED).contains(result.status()));
                successCount++;
            } catch (ComplianceBureauUnavailableException e) {
                // Expected sometimes
            }
        }
        assertTrue(successCount > 0, "At least some calls should succeed");
    }

    @Test
    void shouldThrowCheckedException_sometimes() {
        int exceptionCount = 0;
        for (int i = 0; i < 100; i++) {
            SimulatedComplianceBureauAdapter adapter = new SimulatedComplianceBureauAdapter((long) i);
            try {
                adapter.check("ABC123456");
            } catch (ComplianceBureauUnavailableException e) {
                exceptionCount++;
                // Verify it's a checked exception (ComplianceBureauUnavailableException extends Exception, not RuntimeException)
                assertInstanceOf(Exception.class, e);
                assertFalse(RuntimeException.class.isAssignableFrom(e.getClass()), "Should be a checked exception, not runtime");
            }
        }
        assertTrue(exceptionCount > 0, "At least one call should throw ComplianceBureauUnavailableException");
    }

    @Test
    void shouldSimulateLatency() throws ComplianceBureauUnavailableException {
        // Use a seed that we know produces a successful result
        for (long seed = 0; seed < 20; seed++) {
            SimulatedComplianceBureauAdapter adapter = new SimulatedComplianceBureauAdapter(seed);
            try {
                long start = System.currentTimeMillis();
                adapter.check("ABC123456");
                long elapsed = System.currentTimeMillis() - start;
                assertTrue(elapsed >= 100, "Should simulate at least 100ms latency, was " + elapsed + "ms");
                return; // One successful test is enough
            } catch (ComplianceBureauUnavailableException e) {
                // Try next seed
            }
        }
        fail("Could not find a seed that produces a successful compliance result");
    }
}
