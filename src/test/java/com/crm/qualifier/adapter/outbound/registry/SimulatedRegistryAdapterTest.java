package com.crm.qualifier.adapter.outbound.registry;

import com.crm.qualifier.domain.model.Lead;
import com.crm.qualifier.domain.model.RegistryCheckResult;
import com.crm.qualifier.domain.model.RegistryCheckResult.RegistryStatus;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
class SimulatedRegistryAdapterTest {

    private static final Lead VALID_LEAD = new Lead(
            "ABC123456", LocalDate.of(1990, 1, 15), "John", "Doe", "john@example.com"
    );

    @Test
    void shouldReturnValidRegistryStatus() {
        SimulatedRegistryAdapter adapter = new SimulatedRegistryAdapter();
        RegistryCheckResult result = adapter.check(VALID_LEAD);
        assertNotNull(result);
        assertTrue(Set.of(RegistryStatus.MATCH, RegistryStatus.MISMATCH, RegistryStatus.NOT_FOUND)
                .contains(result.status()));
    }

    @Test
    void shouldSimulateLatency() {
        SimulatedRegistryAdapter adapter = new SimulatedRegistryAdapter();
        long start = System.currentTimeMillis();
        adapter.check(VALID_LEAD);
        long elapsed = System.currentTimeMillis() - start;
        assertTrue(elapsed >= 200, "Should simulate at least 200ms latency, was " + elapsed + "ms");
    }

    @Test
    void shouldProduceDeterministicResults_withSeed() {
        SimulatedRegistryAdapter adapter1 = new SimulatedRegistryAdapter(42L);
        SimulatedRegistryAdapter adapter2 = new SimulatedRegistryAdapter(42L);

        RegistryCheckResult result1 = adapter1.check(VALID_LEAD);
        RegistryCheckResult result2 = adapter2.check(VALID_LEAD);

        assertEquals(result1.status(), result2.status());
    }

    @Test
    void shouldReturnDomainType_notDto() {
        SimulatedRegistryAdapter adapter = new SimulatedRegistryAdapter(42L);
        Object result = adapter.check(VALID_LEAD);
        assertInstanceOf(RegistryCheckResult.class, result);
    }
}
