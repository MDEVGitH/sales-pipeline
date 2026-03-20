package com.crm.qualifier.adapter.inbound.cli;

import com.crm.qualifier.application.port.inbound.QualifyLeadUseCase;
import com.crm.qualifier.domain.exception.InvalidLeadException;
import com.crm.qualifier.domain.exception.QualificationException;
import com.crm.qualifier.domain.model.Lead;
import com.crm.qualifier.domain.model.PipelineResult;

import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.Map;

public class CliAdapter {

    private final QualifyLeadUseCase qualifyLeadUseCase;
    private final LeadRequestMapper requestMapper = new LeadRequestMapper();
    private final QualificationResponseMapper responseMapper = new QualificationResponseMapper();

    public CliAdapter(QualifyLeadUseCase qualifyLeadUseCase) {
        this.qualifyLeadUseCase = qualifyLeadUseCase;
    }

    public void run(String[] args) {
        try {
            Map<String, String> params = parseArgs(args);

            LeadRequest request = new LeadRequest(
                    params.get("nationalId"),
                    params.get("birthdate"),
                    params.get("firstName"),
                    params.get("lastName"),
                    params.get("email")
            );

            Lead lead = requestMapper.toDomain(request);
            PipelineResult result = qualifyLeadUseCase.qualify(lead);
            QualificationResponse response = responseMapper.toResponse(result, lead);

            printResult(response);

        } catch (DateTimeParseException e) {
            System.err.println("ERROR: Invalid date format. Expected YYYY-MM-DD, got: " + e.getParsedString());
            System.exit(1);
        } catch (InvalidLeadException e) {
            System.err.println("ERROR: Invalid lead data — " + e.getMessage());
            System.exit(1);
        } catch (QualificationException e) {
            System.err.println("ERROR: Qualification pipeline failed — " + e.getMessage());
            System.exit(3);
        } catch (Exception e) {
            System.err.println("ERROR: " + e.getMessage());
            System.exit(1);
        }
    }

    private Map<String, String> parseArgs(String[] args) {
        Map<String, String> params = new HashMap<>();
        for (String arg : args) {
            if (arg.startsWith("--") && arg.contains("=")) {
                String key = arg.substring(2, arg.indexOf('='));
                String value = arg.substring(arg.indexOf('=') + 1);
                params.put(key, value);
            }
        }
        return params;
    }

    private void printResult(QualificationResponse response) {
        System.out.println();
        System.out.println("════════════════════════════════════════════════════");
        System.out.println("  QUALIFICATION RESULT");
        System.out.println("════════════════════════════════════════════════════");
        System.out.println("  National ID : " + response.nationalId());
        System.out.println("  Full Name   : " + response.fullName());
        System.out.println("  Status      : " + response.status());
        if (response.score() != null) {
            System.out.println("  Score       : " + response.score() + "/100");
        }
        System.out.println("────────────────────────────────────────────────────");
        System.out.println("  VALIDATION STEPS:");
        for (QualificationResponse.StepResult step : response.steps()) {
            String icon = step.passed() ? "PASS" : "FAIL";
            System.out.println("    [" + icon + "] " + step.name() + ": " + step.detail());
        }
        System.out.println("────────────────────────────────────────────────────");
        System.out.println("  " + response.message());
        System.out.println("════════════════════════════════════════════════════");
        System.out.println();
    }
}
