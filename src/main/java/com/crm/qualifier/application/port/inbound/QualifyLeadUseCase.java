package com.crm.qualifier.application.port.inbound;

import com.crm.qualifier.domain.model.Lead;
import com.crm.qualifier.domain.model.PipelineResult;

public interface QualifyLeadUseCase {
    PipelineResult qualify(Lead lead);
}
