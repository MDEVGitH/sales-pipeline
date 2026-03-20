package com.crm.qualifier.adapter.inbound.cli.mapper;

import com.crm.qualifier.adapter.inbound.cli.dto.QualificationResponse;
import com.crm.qualifier.domain.model.Lead;
import com.crm.qualifier.domain.model.PipelineResult;
import com.crm.qualifier.domain.model.QualificationStatus;
import com.crm.qualifier.domain.model.ValidationResult;

import java.util.List;
import java.util.stream.Collectors;

public class QualificationResponseMapper {

    public QualificationResponse toResponse(PipelineResult result, Lead lead) {
        List<QualificationResponse.StepResult> steps = result.validationResults().stream()
                .map(vr -> new QualificationResponse.StepResult(
                        vr.validatorName(),
                        vr.success(),
                        vr.message()
                ))
                .collect(Collectors.toList());

        Integer score = extractScore(result);

        String message = buildMessage(result);

        return new QualificationResponse(
                lead.nationalId(),
                lead.firstName() + " " + lead.lastName(),
                result.status().name(),
                steps,
                score,
                message
        );
    }

    private Integer extractScore(PipelineResult result) {
        return result.validationResults().stream()
                .filter(vr -> "QualificationScore".equals(vr.validatorName()))
                .findFirst()
                .map(vr -> {
                    String msg = vr.message();
                    try {
                        String scoreStr = msg.substring(msg.indexOf(":") + 2, msg.indexOf("/"));
                        return Integer.parseInt(scoreStr);
                    } catch (Exception e) {
                        return null;
                    }
                })
                .orElse(null);
    }

    private String buildMessage(PipelineResult result) {
        return switch (result.status()) {
            case APPROVED -> "Lead qualified successfully — converted to prospect.";
            case REJECTED -> {
                List<String> failures = result.validationResults().stream()
                        .filter(vr -> !vr.success())
                        .map(ValidationResult::message)
                        .collect(Collectors.toList());
                yield "Lead rejected: " + String.join("; ", failures);
            }
            case MANUAL_REVIEW -> "Lead requires manual review — compliance bureau was unavailable.";
        };
    }
}
