package com.crm.qualifier.domain.model;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
class ValidationResultTest {

    @Test
    void shouldCreatePassResult() {
        ValidationResult result = ValidationResult.pass("Registry", "OK");
        assertTrue(result.success());
        assertEquals("Registry", result.validatorName());
        assertEquals("OK", result.message());
    }

    @Test
    void shouldCreateFailResult() {
        ValidationResult result = ValidationResult.fail("Registry", "Not found");
        assertFalse(result.success());
        assertEquals("Registry", result.validatorName());
        assertEquals("Not found", result.message());
    }

    @Test
    void shouldSetTimestampAutomatically() {
        LocalDateTime before = LocalDateTime.now().minusSeconds(1);
        ValidationResult result = ValidationResult.pass("Test", "test");
        LocalDateTime after = LocalDateTime.now().plusSeconds(1);

        assertNotNull(result.timestamp());
        assertTrue(result.timestamp().isAfter(before));
        assertTrue(result.timestamp().isBefore(after));
    }

    @Test
    void shouldReject_whenValidatorNameIsNull() {
        assertThrows(NullPointerException.class, () -> ValidationResult.pass(null, "OK"));
    }

    @Test
    void shouldReject_whenMessageIsNull() {
        assertThrows(NullPointerException.class, () -> ValidationResult.pass("Registry", null));
    }
}
