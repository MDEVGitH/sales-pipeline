package com.crm.qualifier.domain.model;

import com.crm.qualifier.domain.exception.InvalidLeadException;

import java.time.LocalDate;
import java.time.Period;
import java.util.Objects;

public record Lead(
        String nationalId,
        LocalDate birthdate,
        String firstName,
        String lastName,
        String email
) {
    private static final String EMAIL_PATTERN = "^[^@]+@[^@]+\\.[^@]+$";
    private static final String NAME_PATTERN = "^[a-zA-Z\\s]+$";
    private static final String NATIONAL_ID_PATTERN = "^[a-zA-Z0-9]+$";

    public Lead {
        Objects.requireNonNull(nationalId, "nationalId must not be null");
        Objects.requireNonNull(birthdate, "birthdate must not be null");
        Objects.requireNonNull(firstName, "firstName must not be null");
        Objects.requireNonNull(lastName, "lastName must not be null");
        Objects.requireNonNull(email, "email must not be null");

        if (nationalId.isBlank()) {
            throw new InvalidLeadException("nationalId must not be blank");
        }
        if (nationalId.length() < 6 || nationalId.length() > 20) {
            throw new InvalidLeadException("nationalId must be 6-20 characters");
        }
        if (!nationalId.matches(NATIONAL_ID_PATTERN)) {
            throw new InvalidLeadException("nationalId must be alphanumeric");
        }

        if (!birthdate.isBefore(LocalDate.now())) {
            throw new InvalidLeadException("birthdate must be in the past");
        }
        if (Period.between(birthdate, LocalDate.now()).getYears() < 18) {
            throw new InvalidLeadException("age must be at least 18");
        }

        if (firstName.isBlank()) {
            throw new InvalidLeadException("firstName must not be blank");
        }
        if (firstName.length() > 100) {
            throw new InvalidLeadException("firstName must be 1-100 characters");
        }
        if (!firstName.matches(NAME_PATTERN)) {
            throw new InvalidLeadException("firstName must contain only letters and spaces");
        }

        if (lastName.isBlank()) {
            throw new InvalidLeadException("lastName must not be blank");
        }
        if (lastName.length() > 100) {
            throw new InvalidLeadException("lastName must be 1-100 characters");
        }
        if (!lastName.matches(NAME_PATTERN)) {
            throw new InvalidLeadException("lastName must contain only letters and spaces");
        }

        if (email.isBlank()) {
            throw new InvalidLeadException("email must not be blank");
        }
        if (!email.matches(EMAIL_PATTERN)) {
            throw new InvalidLeadException("email must match pattern: user@domain.tld");
        }
    }
}
