package com.crm.qualifier.adapter.outbound.judicial;

import com.crm.qualifier.domain.model.JudicialCheckResult;
import com.crm.qualifier.domain.model.JudicialCheckResult.JudicialStatus;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
class SimulatedJudicialAdapterTest {

    @Test
    void shouldReturnValidJudicialStatus() {
        SimulatedJudicialAdapter adapter = new SimulatedJudicialAdapter();
        JudicialCheckResult result = adapter.check("ABC123456");
        assertNotNull(result);
        assertTrue(Set.of(JudicialStatus.CLEAN, JudicialStatus.HAS_RECORDS).contains(result.status()));
    }

    @Test
    void shouldSimulateLatency() {
        SimulatedJudicialAdapter adapter = new SimulatedJudicialAdapter();
        long start = System.currentTimeMillis();
        adapter.check("ABC123456");
        long elapsed = System.currentTimeMillis() - start;
        assertTrue(elapsed >= 300, "Should simulate at least 300ms latency, was " + elapsed + "ms");
    }

    @Test
    void shouldReturnDomainType_notDto() {
        SimulatedJudicialAdapter adapter = new SimulatedJudicialAdapter(42L);
        Object result = adapter.check("ABC123456");
        assertInstanceOf(JudicialCheckResult.class, result);
    }
}
