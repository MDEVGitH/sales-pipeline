package com.crm.qualifier.adapter.outbound.registry;

import com.crm.qualifier.domain.model.Lead;
import com.crm.qualifier.domain.model.RegistryCheckResult;
import com.crm.qualifier.domain.model.RegistryCheckResult.RegistryStatus;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
class RegistryMapperTest {

    private final RegistryMapper mapper = new RegistryMapper();
    private final Lead lead = new Lead("ABC123456", LocalDate.of(1990, 1, 15), "John", "Doe", "john@example.com");

    @Test
    void shouldMapToMatch_whenFoundAndDataMatches() {
        RegistryApiResponse dto = new RegistryApiResponse(true, "John", "Doe", "1990-01-15", 0.95);
        RegistryCheckResult result = mapper.toDomain(dto, lead);
        assertEquals(RegistryStatus.MATCH, result.status());
    }

    @Test
    void shouldMapToMismatch_whenFoundButNameDiffers() {
        RegistryApiResponse dto = new RegistryApiResponse(true, "Juan", "Perez", "1990-01-15", 0.3);
        RegistryCheckResult result = mapper.toDomain(dto, lead);
        assertEquals(RegistryStatus.MISMATCH, result.status());
        assertTrue(result.detail().contains("Name mismatch"));
    }

    @Test
    void shouldMapToMismatch_whenFoundButBirthdateDiffers() {
        RegistryApiResponse dto = new RegistryApiResponse(true, "John", "Doe", "1985-06-20", 0.4);
        RegistryCheckResult result = mapper.toDomain(dto, lead);
        assertEquals(RegistryStatus.MISMATCH, result.status());
        assertTrue(result.detail().contains("Birthdate mismatch"));
    }

    @Test
    void shouldMapToNotFound_whenNotFound() {
        RegistryApiResponse dto = new RegistryApiResponse(false, null, null, null, 0.0);
        RegistryCheckResult result = mapper.toDomain(dto, lead);
        assertEquals(RegistryStatus.NOT_FOUND, result.status());
    }

    @Test
    void shouldMatchCaseInsensitive() {
        RegistryApiResponse dto = new RegistryApiResponse(true, "JOHN", "DOE", "1990-01-15", 0.95);
        RegistryCheckResult result = mapper.toDomain(dto, lead);
        assertEquals(RegistryStatus.MATCH, result.status());
    }
}
