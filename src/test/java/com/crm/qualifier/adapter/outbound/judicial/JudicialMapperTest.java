package com.crm.qualifier.adapter.outbound.judicial;

import com.crm.qualifier.domain.model.JudicialCheckResult;
import com.crm.qualifier.domain.model.JudicialCheckResult.JudicialStatus;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
class JudicialMapperTest {

    private final JudicialMapper mapper = new JudicialMapper();

    @Test
    void shouldMapToClean_whenNoRecords() {
        JudicialApiResponse dto = new JudicialApiResponse(false, 0);
        JudicialCheckResult result = mapper.toDomain(dto);
        assertEquals(JudicialStatus.CLEAN, result.status());
    }

    @Test
    void shouldMapToHasRecords_whenRecordsExist() {
        JudicialApiResponse dto = new JudicialApiResponse(true, 3);
        JudicialCheckResult result = mapper.toDomain(dto);
        assertEquals(JudicialStatus.HAS_RECORDS, result.status());
    }
}
