package com.crm.qualifier.domain.model;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record PipelineResult(
        List<ValidationResult> validationResults,
        QualificationStatus status,
        Prospect prospect
) {

    public PipelineResult {
        Objects.requireNonNull(validationResults, "validationResults must not be null");
        Objects.requireNonNull(status, "status must not be null");
        validationResults = Collections.unmodifiableList(validationResults);

        if (status == QualificationStatus.APPROVED && prospect == null) {
            throw new IllegalArgumentException("prospect must be present when status is APPROVED");
        }
        if (status != QualificationStatus.APPROVED && prospect != null) {
            throw new IllegalArgumentException("prospect must be null when status is not APPROVED");
        }
    }

    public Optional<Prospect> getProspect() {
        return Optional.ofNullable(prospect);
    }
}
