package com.crm.qualifier.application.service;

import com.crm.qualifier.application.port.inbound.QualifyLeadUseCase;
import com.crm.qualifier.application.port.outbound.ComplianceBureauPort;
import com.crm.qualifier.application.port.outbound.ComplianceCachePort;
import com.crm.qualifier.application.port.outbound.JudicialPort;
import com.crm.qualifier.application.port.outbound.QualificationScorePort;
import com.crm.qualifier.application.port.outbound.RegistryPort;
import com.crm.qualifier.domain.exception.ComplianceBureauUnavailableException;
import com.crm.qualifier.domain.exception.QualificationException;
import com.crm.qualifier.domain.model.ComplianceCheckResult;
import com.crm.qualifier.domain.model.JudicialCheckResult;
import com.crm.qualifier.domain.model.Lead;
import com.crm.qualifier.domain.model.PipelineResult;
import com.crm.qualifier.domain.model.Prospect;
import com.crm.qualifier.domain.model.QualificationStatus;
import com.crm.qualifier.domain.model.RegistryCheckResult;
import com.crm.qualifier.domain.model.ValidationResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class LeadQualificationService implements QualifyLeadUseCase {

    private final RegistryPort registryPort;
    private final JudicialPort judicialPort;
    private final ComplianceBureauPort complianceBureauPort;
    private final QualificationScorePort qualificationScorePort;
    private final ComplianceCachePort complianceCachePort;

    public LeadQualificationService(
            RegistryPort registryPort,
            JudicialPort judicialPort,
            ComplianceBureauPort complianceBureauPort,
            QualificationScorePort qualificationScorePort,
            ComplianceCachePort complianceCachePort) {
        this.registryPort = registryPort;
        this.judicialPort = judicialPort;
        this.complianceBureauPort = complianceBureauPort;
        this.qualificationScorePort = qualificationScorePort;
        this.complianceCachePort = complianceCachePort;
    }

    @Override
    public PipelineResult qualify(Lead lead) {
        try {
            System.out.println("[PIPELINE] Starting qualification for nationalId=" + lead.nationalId());

            // Phase 1: Parallel execution of Registry + Judicial
            CompletableFuture<ValidationResult> registryFuture =
                    CompletableFuture.supplyAsync(() -> executeRegistryCheck(lead));
            CompletableFuture<ValidationResult> judicialFuture =
                    CompletableFuture.supplyAsync(() -> executeJudicialCheck(lead));

            CompletableFuture.allOf(registryFuture, judicialFuture)
                    .get(10, TimeUnit.SECONDS);

            ValidationResult registryResult = registryFuture.get();
            ValidationResult judicialResult = judicialFuture.get();

            List<ValidationResult> results = new ArrayList<>();
            results.add(registryResult);
            results.add(judicialResult);

            // Short-circuit: if either parallel step failed
            if (!registryResult.success() || !judicialResult.success()) {
                System.out.println("[PIPELINE] Qualification REJECTED — parallel validation failed");
                return new PipelineResult(results, QualificationStatus.REJECTED, null);
            }

            // Phase 2: Compliance Bureau (sequential)
            ValidationResult complianceResult = executeComplianceCheck(lead.nationalId());

            if (complianceResult == null) {
                // MANUAL_REVIEW signal
                System.out.println("[PIPELINE] Qualification requires MANUAL_REVIEW — compliance bureau unavailable");
                return new PipelineResult(results, QualificationStatus.MANUAL_REVIEW, null);
            }

            results.add(complianceResult);

            if (!complianceResult.success()) {
                System.out.println("[PIPELINE] Qualification REJECTED — compliance check failed");
                return new PipelineResult(results, QualificationStatus.REJECTED, null);
            }

            // Phase 3: Qualification Score (sequential)
            ValidationResult scoreResult = executeScoreCheck();
            results.add(scoreResult);

            if (!scoreResult.success()) {
                System.out.println("[PIPELINE] Qualification REJECTED — score below threshold");
                return new PipelineResult(results, QualificationStatus.REJECTED, null);
            }

            // All passed
            int score = extractScore(scoreResult);
            Prospect prospect = Prospect.fromLead(lead, score);
            System.out.println("[PIPELINE] Qualification APPROVED — lead converted to prospect");
            return new PipelineResult(results, QualificationStatus.APPROVED, prospect);

        } catch (Exception e) {
            throw new QualificationException("Unexpected error during qualification pipeline", e);
        }
    }

    private ValidationResult executeRegistryCheck(Lead lead) {
        RegistryCheckResult result = registryPort.check(lead);
        return switch (result.status()) {
            case MATCH -> ValidationResult.pass("NationalRegistry", "Person verified in national registry");
            case MISMATCH -> ValidationResult.fail("NationalRegistry", "Data mismatch: " + result.detail());
            case NOT_FOUND -> ValidationResult.fail("NationalRegistry", "National ID not found in registry");
        };
    }

    private ValidationResult executeJudicialCheck(Lead lead) {
        JudicialCheckResult result = judicialPort.check(lead.nationalId());
        return switch (result.status()) {
            case CLEAN -> ValidationResult.pass("JudicialRecords", "No judicial records found");
            case HAS_RECORDS -> ValidationResult.fail("JudicialRecords", "Judicial records found for this person");
        };
    }

    /**
     * Returns null as a MANUAL_REVIEW signal when the bureau is unavailable.
     */
    private ValidationResult executeComplianceCheck(String nationalId) {
        // Check cache first
        Optional<ComplianceCheckResult> cached = complianceCachePort.get(nationalId);
        ComplianceCheckResult complianceResult;

        if (cached.isPresent()) {
            System.out.println("[COMPLIANCE] Cache HIT for nationalId=" + nationalId);
            complianceResult = cached.get();
        } else {
            System.out.println("[COMPLIANCE] Cache MISS — calling external service");
            try {
                complianceResult = complianceBureauPort.check(nationalId);
                complianceCachePort.put(nationalId, complianceResult);
            } catch (ComplianceBureauUnavailableException e) {
                System.out.println("[COMPLIANCE] Service UNAVAILABLE");
                return null; // MANUAL_REVIEW signal
            }
        }

        return switch (complianceResult.status()) {
            case CLEAR -> ValidationResult.pass("ComplianceBureau", "Not found in sanctions list");
            case FLAGGED -> ValidationResult.fail("ComplianceBureau", "FLAGGED in sanctions/OFAC list");
        };
    }

    private ValidationResult executeScoreCheck() {
        int score = qualificationScorePort.generateScore();
        System.out.println("[SCORE] Generated score: " + score + "/100 (threshold: >60)");
        if (score > 60) {
            return ValidationResult.pass("QualificationScore", "Score: " + score + "/100 (threshold: >60)");
        } else {
            return ValidationResult.fail("QualificationScore", "Score: " + score + "/100 (threshold: >60, not met)");
        }
    }

    private int extractScore(ValidationResult scoreResult) {
        String message = scoreResult.message();
        // Parse "Score: XX/100 ..." format
        String scoreStr = message.substring(message.indexOf(":") + 2, message.indexOf("/"));
        return Integer.parseInt(scoreStr);
    }
}
