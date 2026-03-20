package com.crm.qualifier.application.port.outbound;

import com.crm.qualifier.domain.model.JudicialCheckResult;

public interface JudicialPort {
    JudicialCheckResult check(String nationalId);
}
