package com.crm.qualifier.application.port.outbound;

import com.crm.qualifier.domain.exception.ComplianceBureauUnavailableException;
import com.crm.qualifier.domain.model.ComplianceCheckResult;

public interface ComplianceBureauPort {
    ComplianceCheckResult check(String nationalId) throws ComplianceBureauUnavailableException;
}
