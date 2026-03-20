package com.crm.qualifier.adapter.inbound.cli;

import java.util.List;

public record QualificationResponse(
        String nationalId,
        String fullName,
        String status,
        List<StepResult> steps,
        Integer score,
        String message
) {
    public record StepResult(
            String name,
            boolean passed,
            String detail
    ) {}
}
