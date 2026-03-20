package com.crm.qualifier.domain.model;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
class PipelineResultTest {

    private static final Lead VALID_LEAD = new Lead(
            "ABC123456", LocalDate.of(1990, 1, 15), "John", "Doe", "john@example.com"
    );

    private static final Prospect VALID_PROSPECT = Prospect.fromLead(VALID_LEAD, 75);

    @Test
    void shouldCreateApprovedResultWithProspect() {
        List<ValidationResult> results = List.of(
                ValidationResult.pass("Registry", "OK"),
                ValidationResult.pass("Judicial", "Clean"),
                ValidationResult.pass("Compliance", "Clear"),
                ValidationResult.pass("Score", "75/100")
        );

        PipelineResult pipelineResult = new PipelineResult(results, QualificationStatus.APPROVED, VALID_PROSPECT);
        assertTrue(pipelineResult.getProspect().isPresent());
        assertEquals(QualificationStatus.APPROVED, pipelineResult.status());
    }

    @Test
    void shouldCreateRejectedResultWithoutProspect() {
        List<ValidationResult> results = List.of(
                ValidationResult.fail("Registry", "Not found")
        );

        PipelineResult pipelineResult = new PipelineResult(results, QualificationStatus.REJECTED, null);
        assertTrue(pipelineResult.getProspect().isEmpty());
        assertEquals(QualificationStatus.REJECTED, pipelineResult.status());
    }

    @Test
    void shouldCreateManualReviewResult() {
        List<ValidationResult> results = List.of(
                ValidationResult.pass("Registry", "OK"),
                ValidationResult.pass("Judicial", "Clean")
        );

        PipelineResult pipelineResult = new PipelineResult(results, QualificationStatus.MANUAL_REVIEW, null);
        assertEquals(QualificationStatus.MANUAL_REVIEW, pipelineResult.status());
        assertTrue(pipelineResult.getProspect().isEmpty());
    }

    @Test
    void shouldReject_whenApprovedButNoProspect() {
        List<ValidationResult> results = List.of(ValidationResult.pass("Test", "OK"));
        assertThrows(IllegalArgumentException.class,
                () -> new PipelineResult(results, QualificationStatus.APPROVED, null));
    }

    @Test
    void shouldReject_whenRejectedButHasProspect() {
        List<ValidationResult> results = List.of(ValidationResult.fail("Test", "Fail"));
        assertThrows(IllegalArgumentException.class,
                () -> new PipelineResult(results, QualificationStatus.REJECTED, VALID_PROSPECT));
    }

    @Test
    void shouldHaveUnmodifiableResultsList() {
        List<ValidationResult> results = new ArrayList<>();
        results.add(ValidationResult.pass("Registry", "OK"));

        PipelineResult pipelineResult = new PipelineResult(results, QualificationStatus.REJECTED, null);
        assertThrows(UnsupportedOperationException.class,
                () -> pipelineResult.validationResults().add(ValidationResult.pass("Extra", "test")));
    }
}
