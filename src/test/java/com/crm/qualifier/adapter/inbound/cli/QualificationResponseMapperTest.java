package com.crm.qualifier.adapter.inbound.cli;

import com.crm.qualifier.domain.model.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
class QualificationResponseMapperTest {

    private final QualificationResponseMapper mapper = new QualificationResponseMapper();
    private final Lead lead = new Lead("ABC123456", LocalDate.of(1990, 1, 15), "John", "Doe", "john@example.com");

    @Test
    void shouldMapApprovedResult() {
        Prospect prospect = Prospect.fromLead(lead, 75);
        List<ValidationResult> results = List.of(
                ValidationResult.pass("NationalRegistry", "Person verified in national registry"),
                ValidationResult.pass("JudicialRecords", "No judicial records found"),
                ValidationResult.pass("ComplianceBureau", "Not found in sanctions list"),
                ValidationResult.pass("QualificationScore", "Score: 75/100 (threshold: >60)")
        );
        PipelineResult pipelineResult = new PipelineResult(results, QualificationStatus.APPROVED, prospect);

        QualificationResponse response = mapper.toResponse(pipelineResult, lead);
        assertEquals("ABC123456", response.nationalId());
        assertEquals("John Doe", response.fullName());
        assertEquals("APPROVED", response.status());
        assertEquals(4, response.steps().size());
        assertEquals(75, response.score());
        assertTrue(response.message().contains("qualified successfully"));
    }

    @Test
    void shouldMapRejectedResult() {
        List<ValidationResult> results = List.of(
                ValidationResult.fail("NationalRegistry", "National ID not found in registry"),
                ValidationResult.pass("JudicialRecords", "No judicial records found")
        );
        PipelineResult pipelineResult = new PipelineResult(results, QualificationStatus.REJECTED, null);

        QualificationResponse response = mapper.toResponse(pipelineResult, lead);
        assertEquals("REJECTED", response.status());
        assertEquals(2, response.steps().size());
        assertNull(response.score());
        assertTrue(response.message().contains("rejected"));
    }

    @Test
    void shouldMapManualReviewResult() {
        List<ValidationResult> results = List.of(
                ValidationResult.pass("NationalRegistry", "Person verified in national registry"),
                ValidationResult.pass("JudicialRecords", "No judicial records found")
        );
        PipelineResult pipelineResult = new PipelineResult(results, QualificationStatus.MANUAL_REVIEW, null);

        QualificationResponse response = mapper.toResponse(pipelineResult, lead);
        assertEquals("MANUAL_REVIEW", response.status());
        assertEquals(2, response.steps().size());
        assertTrue(response.message().contains("manual review"));
    }

    @Test
    void shouldMapStepDetails() {
        List<ValidationResult> results = List.of(
                ValidationResult.pass("NationalRegistry", "Person verified in national registry")
        );
        PipelineResult pipelineResult = new PipelineResult(results, QualificationStatus.REJECTED, null);

        QualificationResponse response = mapper.toResponse(pipelineResult, lead);
        QualificationResponse.StepResult step = response.steps().get(0);
        assertEquals("NationalRegistry", step.name());
        assertTrue(step.passed());
        assertEquals("Person verified in national registry", step.detail());
    }
}
