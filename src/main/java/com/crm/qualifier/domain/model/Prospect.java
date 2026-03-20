package com.crm.qualifier.domain.model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;

public record Prospect(
        String nationalId,
        LocalDate birthdate,
        String firstName,
        String lastName,
        String email,
        int qualificationScore,
        LocalDateTime qualifiedAt
) {

    public Prospect {
        Objects.requireNonNull(nationalId, "nationalId must not be null");
        Objects.requireNonNull(birthdate, "birthdate must not be null");
        Objects.requireNonNull(firstName, "firstName must not be null");
        Objects.requireNonNull(lastName, "lastName must not be null");
        Objects.requireNonNull(email, "email must not be null");
        Objects.requireNonNull(qualifiedAt, "qualifiedAt must not be null");

        if (qualificationScore < 61 || qualificationScore > 100) {
            throw new IllegalArgumentException("qualificationScore must be between 61 and 100, got: " + qualificationScore);
        }
        if (qualifiedAt.isAfter(LocalDateTime.now().plusSeconds(1))) {
            throw new IllegalArgumentException("qualifiedAt must not be in the future");
        }
    }

    public static Prospect fromLead(Lead lead, int score) {
        return new Prospect(
                lead.nationalId(),
                lead.birthdate(),
                lead.firstName(),
                lead.lastName(),
                lead.email(),
                score,
                LocalDateTime.now()
        );
    }
}
