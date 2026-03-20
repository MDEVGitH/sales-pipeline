package com.crm.qualifier.adapter.outbound.compliance;

import com.crm.qualifier.adapter.outbound.compliance.dto.ComplianceApiResponse;
import com.crm.qualifier.adapter.outbound.compliance.mapper.ComplianceMapper;
import com.crm.qualifier.domain.model.ComplianceCheckResult;
import com.crm.qualifier.domain.model.ComplianceCheckResult.ComplianceStatus;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
class ComplianceMapperTest {

    private final ComplianceMapper mapper = new ComplianceMapper();

    @Test
    void shouldMapToClear() {
        ComplianceApiResponse dto = new ComplianceApiResponse("CLEAR", "2026-03-20T10:00:00", "OFAC");
        ComplianceCheckResult result = mapper.toDomain(dto);
        assertEquals(ComplianceStatus.CLEAR, result.status());
    }

    @Test
    void shouldMapToFlagged() {
        ComplianceApiResponse dto = new ComplianceApiResponse("FLAGGED", "2026-03-20T10:00:00", "OFAC");
        ComplianceCheckResult result = mapper.toDomain(dto);
        assertEquals(ComplianceStatus.FLAGGED, result.status());
    }

    @Test
    void shouldHandleLowercaseStatus() {
        ComplianceApiResponse dto = new ComplianceApiResponse("clear", "2026-03-20T10:00:00", "OFAC");
        ComplianceCheckResult result = mapper.toDomain(dto);
        assertEquals(ComplianceStatus.CLEAR, result.status());
    }

    @Test
    void shouldThrow_whenUnknownStatus() {
        ComplianceApiResponse dto = new ComplianceApiResponse("UNKNOWN", "2026-03-20T10:00:00", "OFAC");
        assertThrows(IllegalArgumentException.class, () -> mapper.toDomain(dto));
    }
}
