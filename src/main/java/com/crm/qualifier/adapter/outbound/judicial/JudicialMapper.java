package com.crm.qualifier.adapter.outbound.judicial;

import com.crm.qualifier.domain.model.JudicialCheckResult;
import com.crm.qualifier.domain.model.JudicialCheckResult.JudicialStatus;

public class JudicialMapper {

    public JudicialCheckResult toDomain(JudicialApiResponse response) {
        JudicialStatus status = response.hasRecords()
                ? JudicialStatus.HAS_RECORDS
                : JudicialStatus.CLEAN;
        return new JudicialCheckResult(status);
    }
}
