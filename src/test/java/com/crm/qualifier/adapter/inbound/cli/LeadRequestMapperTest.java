package com.crm.qualifier.adapter.inbound.cli;

import com.crm.qualifier.domain.exception.InvalidLeadException;
import com.crm.qualifier.domain.model.Lead;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
class LeadRequestMapperTest {

    private final LeadRequestMapper mapper = new LeadRequestMapper();

    @Test
    void shouldMapValidRequest() {
        LeadRequest request = new LeadRequest("ABC123456", "1990-01-15", "John", "Doe", "john@example.com");
        Lead lead = mapper.toDomain(request);
        assertEquals("ABC123456", lead.nationalId());
        assertEquals(LocalDate.of(1990, 1, 15), lead.birthdate());
        assertEquals("John", lead.firstName());
        assertEquals("Doe", lead.lastName());
        assertEquals("john@example.com", lead.email());
    }

    @Test
    void shouldThrow_whenBirthdateFormatInvalid() {
        LeadRequest request = new LeadRequest("ABC123456", "not-a-date", "John", "Doe", "john@example.com");
        assertThrows(DateTimeParseException.class, () -> mapper.toDomain(request));
    }

    @Test
    void shouldThrow_whenLeadValidationFails() {
        LeadRequest request = new LeadRequest("", "1990-01-15", "John", "Doe", "john@example.com");
        assertThrows(InvalidLeadException.class, () -> mapper.toDomain(request));
    }
}
