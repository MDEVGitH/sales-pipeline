package com.crm.qualifier.application.port.outbound;

import com.crm.qualifier.domain.model.ComplianceCheckResult;

import java.util.Optional;

public interface ComplianceCachePort {
    Optional<ComplianceCheckResult> get(String nationalId);
    void put(String nationalId, ComplianceCheckResult result);
}
