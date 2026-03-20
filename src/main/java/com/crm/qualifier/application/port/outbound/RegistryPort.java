package com.crm.qualifier.application.port.outbound;

import com.crm.qualifier.domain.model.Lead;
import com.crm.qualifier.domain.model.RegistryCheckResult;

public interface RegistryPort {
    RegistryCheckResult check(Lead lead);
}
