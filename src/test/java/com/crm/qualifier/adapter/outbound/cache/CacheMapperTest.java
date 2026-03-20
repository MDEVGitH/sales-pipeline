package com.crm.qualifier.adapter.outbound.cache;

import com.crm.qualifier.domain.model.ComplianceCheckResult;
import com.crm.qualifier.domain.model.ComplianceCheckResult.ComplianceStatus;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
class CacheMapperTest {

    private final CacheMapper mapper = new CacheMapper();

    @Test
    void shouldConvertDomainToDto() {
        ComplianceCheckResult result = new ComplianceCheckResult(ComplianceStatus.CLEAR);
        CacheEntryDto dto = mapper.toDto(result);
        assertEquals("CLEAR", dto.status);
        assertNotNull(dto.timestamp);
    }

    @Test
    void shouldConvertDtoToDomain() {
        CacheEntryDto dto = new CacheEntryDto();
        dto.status = "FLAGGED";
        dto.timestamp = LocalDateTime.now().toString();
        ComplianceCheckResult result = mapper.toDomain(dto);
        assertEquals(ComplianceStatus.FLAGGED, result.status());
    }

    @Test
    void shouldDetectExpiredEntry() {
        CacheEntryDto dto = new CacheEntryDto();
        dto.status = "CLEAR";
        dto.timestamp = LocalDateTime.now().minusHours(25).toString();
        assertTrue(mapper.isExpired(dto, 24));
    }

    @Test
    void shouldDetectValidEntry() {
        CacheEntryDto dto = new CacheEntryDto();
        dto.status = "CLEAR";
        dto.timestamp = LocalDateTime.now().minusHours(1).toString();
        assertFalse(mapper.isExpired(dto, 24));
    }

    @Test
    void shouldDetectBoundaryExpiration() {
        CacheEntryDto dto = new CacheEntryDto();
        dto.status = "CLEAR";
        // Exactly 24 hours ago + a small buffer to ensure it's definitely expired
        dto.timestamp = LocalDateTime.now().minusHours(24).minusSeconds(1).toString();
        assertTrue(mapper.isExpired(dto, 24));
    }
}
