package com.crm.qualifier.adapter.outbound.judicial;

import com.crm.qualifier.adapter.outbound.judicial.dto.JudicialApiResponse;
import com.crm.qualifier.adapter.outbound.judicial.mapper.JudicialMapper;
import com.crm.qualifier.application.port.outbound.JudicialPort;
import com.crm.qualifier.domain.model.JudicialCheckResult;

import java.util.Random;

public class SimulatedJudicialAdapter implements JudicialPort {

    private final Random random;
    private final JudicialMapper mapper = new JudicialMapper();

    public SimulatedJudicialAdapter() {
        this.random = new Random();
    }

    public SimulatedJudicialAdapter(long seed) {
        this.random = new Random(seed);
    }

    @Override
    public JudicialCheckResult check(String nationalId) {
        long startTime = System.currentTimeMillis();

        // Simulate latency: 300-1000ms
        long latency = 300 + random.nextLong(701);
        try {
            Thread.sleep(latency);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Generate random outcome
        double outcome = random.nextDouble();
        JudicialApiResponse dto;

        if (outcome < 0.85) {
            // 85% — no records
            dto = new JudicialApiResponse(false, 0);
        } else {
            // 15% — has records
            int recordCount = 1 + random.nextInt(5);
            dto = new JudicialApiResponse(true, recordCount);
        }

        JudicialCheckResult result = mapper.toDomain(dto);
        long elapsed = System.currentTimeMillis() - startTime;
        System.out.println("[JUDICIAL] Checking nationalId=" + nationalId
                + "... " + result.status() + " (" + elapsed + "ms)");
        return result;
    }
}
