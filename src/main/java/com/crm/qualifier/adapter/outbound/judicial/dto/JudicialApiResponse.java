package com.crm.qualifier.adapter.outbound.judicial.dto;

public record JudicialApiResponse(
        boolean hasRecords,
        int recordCount
) {}
