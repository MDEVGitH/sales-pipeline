package com.crm.qualifier.adapter.outbound.compliance;

public record ComplianceApiResponse(
        String status,
        String checkedAt,
        String source
) {}
