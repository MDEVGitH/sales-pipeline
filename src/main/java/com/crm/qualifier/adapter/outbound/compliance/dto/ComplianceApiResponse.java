package com.crm.qualifier.adapter.outbound.compliance.dto;

public record ComplianceApiResponse(
        String status,
        String checkedAt,
        String source
) {}
