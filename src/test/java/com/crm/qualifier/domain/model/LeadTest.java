package com.crm.qualifier.domain.model;

import com.crm.qualifier.domain.exception.InvalidLeadException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
class LeadTest {

    private static final String VALID_NATIONAL_ID = "ABC123456";
    private static final LocalDate VALID_BIRTHDATE = LocalDate.of(1990, 1, 15);
    private static final String VALID_FIRST_NAME = "John";
    private static final String VALID_LAST_NAME = "Doe";
    private static final String VALID_EMAIL = "john@example.com";

    @Test
    void shouldCreateValidLead() {
        Lead lead = new Lead(VALID_NATIONAL_ID, VALID_BIRTHDATE, VALID_FIRST_NAME, VALID_LAST_NAME, VALID_EMAIL);
        assertNotNull(lead);
        assertEquals(VALID_NATIONAL_ID, lead.nationalId());
        assertEquals(VALID_BIRTHDATE, lead.birthdate());
        assertEquals(VALID_FIRST_NAME, lead.firstName());
        assertEquals(VALID_LAST_NAME, lead.lastName());
        assertEquals(VALID_EMAIL, lead.email());
    }

    @Test
    void shouldReject_whenNationalIdIsNull() {
        assertThrows(NullPointerException.class,
                () -> new Lead(null, VALID_BIRTHDATE, VALID_FIRST_NAME, VALID_LAST_NAME, VALID_EMAIL));
    }

    @Test
    void shouldReject_whenNationalIdIsBlank() {
        assertThrows(InvalidLeadException.class,
                () -> new Lead("", VALID_BIRTHDATE, VALID_FIRST_NAME, VALID_LAST_NAME, VALID_EMAIL));
    }

    @Test
    void shouldReject_whenBirthdateIsNull() {
        assertThrows(NullPointerException.class,
                () -> new Lead(VALID_NATIONAL_ID, null, VALID_FIRST_NAME, VALID_LAST_NAME, VALID_EMAIL));
    }

    @Test
    void shouldReject_whenBirthdateIsInFuture() {
        LocalDate future = LocalDate.now().plusDays(1);
        assertThrows(InvalidLeadException.class,
                () -> new Lead(VALID_NATIONAL_ID, future, VALID_FIRST_NAME, VALID_LAST_NAME, VALID_EMAIL));
    }

    @Test
    void shouldReject_whenBirthdateIsToday() {
        LocalDate today = LocalDate.now();
        assertThrows(InvalidLeadException.class,
                () -> new Lead(VALID_NATIONAL_ID, today, VALID_FIRST_NAME, VALID_LAST_NAME, VALID_EMAIL));
    }

    @Test
    void shouldReject_whenAgeIsUnder18() {
        LocalDate tenYearsAgo = LocalDate.now().minusYears(10);
        assertThrows(InvalidLeadException.class,
                () -> new Lead(VALID_NATIONAL_ID, tenYearsAgo, VALID_FIRST_NAME, VALID_LAST_NAME, VALID_EMAIL));
    }

    @Test
    void shouldReject_whenFirstNameIsNull() {
        assertThrows(NullPointerException.class,
                () -> new Lead(VALID_NATIONAL_ID, VALID_BIRTHDATE, null, VALID_LAST_NAME, VALID_EMAIL));
    }

    @Test
    void shouldReject_whenFirstNameIsBlank() {
        assertThrows(InvalidLeadException.class,
                () -> new Lead(VALID_NATIONAL_ID, VALID_BIRTHDATE, " ", VALID_LAST_NAME, VALID_EMAIL));
    }

    @Test
    void shouldReject_whenLastNameIsNull() {
        assertThrows(NullPointerException.class,
                () -> new Lead(VALID_NATIONAL_ID, VALID_BIRTHDATE, VALID_FIRST_NAME, null, VALID_EMAIL));
    }

    @Test
    void shouldReject_whenLastNameIsBlank() {
        assertThrows(InvalidLeadException.class,
                () -> new Lead(VALID_NATIONAL_ID, VALID_BIRTHDATE, VALID_FIRST_NAME, "", VALID_EMAIL));
    }

    @Test
    void shouldReject_whenEmailIsNull() {
        assertThrows(NullPointerException.class,
                () -> new Lead(VALID_NATIONAL_ID, VALID_BIRTHDATE, VALID_FIRST_NAME, VALID_LAST_NAME, null));
    }

    @Test
    void shouldReject_whenEmailIsBlank() {
        assertThrows(InvalidLeadException.class,
                () -> new Lead(VALID_NATIONAL_ID, VALID_BIRTHDATE, VALID_FIRST_NAME, VALID_LAST_NAME, ""));
    }

    @Test
    void shouldReject_whenEmailHasNoAtSign() {
        assertThrows(InvalidLeadException.class,
                () -> new Lead(VALID_NATIONAL_ID, VALID_BIRTHDATE, VALID_FIRST_NAME, VALID_LAST_NAME, "invalid"));
    }

    @Test
    void shouldReject_whenEmailHasNoDomain() {
        assertThrows(InvalidLeadException.class,
                () -> new Lead(VALID_NATIONAL_ID, VALID_BIRTHDATE, VALID_FIRST_NAME, VALID_LAST_NAME, "user@"));
    }

    @Test
    void shouldAcceptValidEmail() {
        Lead lead = new Lead(VALID_NATIONAL_ID, VALID_BIRTHDATE, VALID_FIRST_NAME, VALID_LAST_NAME, "user@domain.com");
        assertEquals("user@domain.com", lead.email());
    }
}
