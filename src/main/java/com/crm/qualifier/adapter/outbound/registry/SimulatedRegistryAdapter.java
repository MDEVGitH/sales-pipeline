package com.crm.qualifier.adapter.outbound.registry;

import com.crm.qualifier.adapter.outbound.registry.dto.RegistryApiResponse;
import com.crm.qualifier.adapter.outbound.registry.mapper.RegistryMapper;
import com.crm.qualifier.application.port.outbound.RegistryPort;
import com.crm.qualifier.domain.model.Lead;
import com.crm.qualifier.domain.model.RegistryCheckResult;

import java.util.Random;

public class SimulatedRegistryAdapter implements RegistryPort {

    private final Random random;
    private final RegistryMapper mapper = new RegistryMapper();

    public SimulatedRegistryAdapter() {
        this.random = new Random();
    }

    public SimulatedRegistryAdapter(long seed) {
        this.random = new Random(seed);
    }

    @Override
    public RegistryCheckResult check(Lead lead) {
        long startTime = System.currentTimeMillis();

        // Simulate latency: 200-800ms
        long latency = 200 + random.nextLong(601);
        try {
            Thread.sleep(latency);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Generate random outcome
        double outcome = random.nextDouble();
        RegistryApiResponse dto;

        if (outcome < 0.70) {
            // 70% — found and matching
            dto = new RegistryApiResponse(
                    true,
                    lead.firstName(),
                    lead.lastName(),
                    lead.birthdate().toString(),
                    0.95
            );
        } else if (outcome < 0.90) {
            // 20% — not found
            dto = new RegistryApiResponse(false, null, null, null, 0.0);
        } else {
            // 10% — found but mismatched
            dto = new RegistryApiResponse(
                    true,
                    "Unknown",
                    "Person",
                    "1980-01-01",
                    0.3
            );
        }

        RegistryCheckResult result = mapper.toDomain(dto, lead);
        long elapsed = System.currentTimeMillis() - startTime;
        System.out.println("[REGISTRY] Checking nationalId=" + lead.nationalId()
                + "... " + result.status() + " (" + elapsed + "ms)");
        return result;
    }
}
