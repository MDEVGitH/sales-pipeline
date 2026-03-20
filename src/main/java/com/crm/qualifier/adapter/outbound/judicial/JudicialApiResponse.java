package com.crm.qualifier.adapter.outbound.judicial;

public record JudicialApiResponse(
        boolean hasRecords,
        int recordCount
) {}
