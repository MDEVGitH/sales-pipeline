package com.crm.qualifier.domain.model;

public record RegistryCheckResult(RegistryStatus status, String detail) {

    public enum RegistryStatus {
        MATCH,
        MISMATCH,
        NOT_FOUND
    }
}
