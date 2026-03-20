package com.crm.qualifier.domain.model;

public record ComplianceCheckResult(ComplianceStatus status) {

    public enum ComplianceStatus {
        CLEAR,
        FLAGGED
    }
}
