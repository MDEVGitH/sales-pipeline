package com.crm.qualifier.adapter.outbound.compliance;

import com.crm.qualifier.application.port.outbound.ComplianceBureauPort;
import com.crm.qualifier.domain.exception.ComplianceBureauUnavailableException;
import com.crm.qualifier.domain.model.ComplianceCheckResult;

import java.time.LocalDateTime;
import java.util.Random;

public class SimulatedComplianceBureauAdapter implements ComplianceBureauPort {

    private final Random random;
    private final ComplianceMapper mapper = new ComplianceMapper();

    public SimulatedComplianceBureauAdapter() {
        this.random = new Random();
    }

    public SimulatedComplianceBureauAdapter(long seed) {
        this.random = new Random(seed);
    }

    @Override
    public ComplianceCheckResult check(String nationalId) throws ComplianceBureauUnavailableException {
        long startTime = System.currentTimeMillis();

        // Simulate latency: 100-500ms
        long latency = 100 + random.nextLong(401);
        try {
            Thread.sleep(latency);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Generate random outcome
        double outcome = random.nextDouble();

        if (outcome < 0.80) {
            // 80% — CLEAR
            ComplianceApiResponse dto = new ComplianceApiResponse(
                    "CLEAR",
                    LocalDateTime.now().toString(),
                    "OFAC"
            );
            ComplianceCheckResult result = mapper.toDomain(dto);
            long elapsed = System.currentTimeMillis() - startTime;
            System.out.println("[COMPLIANCE] Service call for nationalId=" + nationalId
                    + "... CLEAR (" + elapsed + "ms)");
            return result;
        } else if (outcome < 0.90) {
            // 10% — FLAGGED
            ComplianceApiResponse dto = new ComplianceApiResponse(
                    "FLAGGED",
                    LocalDateTime.now().toString(),
                    "OFAC"
            );
            ComplianceCheckResult result = mapper.toDomain(dto);
            long elapsed = System.currentTimeMillis() - startTime;
            System.out.println("[COMPLIANCE] Service call for nationalId=" + nationalId
                    + "... FLAGGED (" + elapsed + "ms)");
            return result;
        } else {
            // 10% — Service unavailable
            System.out.println("[COMPLIANCE] Service UNAVAILABLE for nationalId=" + nationalId);
            throw new ComplianceBureauUnavailableException("Compliance Bureau service unavailable");
        }
    }
}
