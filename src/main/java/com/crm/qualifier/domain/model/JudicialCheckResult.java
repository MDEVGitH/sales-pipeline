package com.crm.qualifier.domain.model;

public record JudicialCheckResult(JudicialStatus status) {

    public enum JudicialStatus {
        CLEAN,
        HAS_RECORDS
    }
}
