package com.crm.qualifier.domain.model;

import java.time.LocalDateTime;
import java.util.Objects;

public record ValidationResult(
        boolean success,
        String validatorName,
        String message,
        LocalDateTime timestamp
) {

    public ValidationResult {
        Objects.requireNonNull(validatorName, "validatorName must not be null");
        Objects.requireNonNull(message, "message must not be null");
        Objects.requireNonNull(timestamp, "timestamp must not be null");
    }

    public static ValidationResult pass(String validatorName, String message) {
        return new ValidationResult(true, validatorName, message, LocalDateTime.now());
    }

    public static ValidationResult fail(String validatorName, String message) {
        return new ValidationResult(false, validatorName, message, LocalDateTime.now());
    }
}
