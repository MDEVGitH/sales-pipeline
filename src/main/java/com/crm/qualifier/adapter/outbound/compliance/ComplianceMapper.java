package com.crm.qualifier.adapter.outbound.compliance;

import com.crm.qualifier.domain.model.ComplianceCheckResult;
import com.crm.qualifier.domain.model.ComplianceCheckResult.ComplianceStatus;

public class ComplianceMapper {

    public ComplianceCheckResult toDomain(ComplianceApiResponse response) {
        ComplianceStatus status = ComplianceStatus.valueOf(response.status().toUpperCase());
        return new ComplianceCheckResult(status);
    }
}
