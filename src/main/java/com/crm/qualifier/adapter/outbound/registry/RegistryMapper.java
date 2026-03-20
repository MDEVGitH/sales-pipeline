package com.crm.qualifier.adapter.outbound.registry;

import com.crm.qualifier.domain.model.Lead;
import com.crm.qualifier.domain.model.RegistryCheckResult;
import com.crm.qualifier.domain.model.RegistryCheckResult.RegistryStatus;

import java.time.LocalDate;

public class RegistryMapper {

    public RegistryCheckResult toDomain(RegistryApiResponse response, Lead lead) {
        if (!response.found()) {
            return new RegistryCheckResult(RegistryStatus.NOT_FOUND, "National ID not found in registry");
        }

        boolean nameMatches = lead.firstName().equalsIgnoreCase(response.firstName())
                && lead.lastName().equalsIgnoreCase(response.lastName());
        boolean birthdateMatches = lead.birthdate().equals(LocalDate.parse(response.birthdate()));

        if (nameMatches && birthdateMatches) {
            return new RegistryCheckResult(RegistryStatus.MATCH, "Person verified in national registry");
        }

        String mismatchDetail = buildMismatchDetail(response, lead);
        return new RegistryCheckResult(RegistryStatus.MISMATCH, mismatchDetail);
    }

    private String buildMismatchDetail(RegistryApiResponse response, Lead lead) {
        StringBuilder detail = new StringBuilder();
        if (!lead.firstName().equalsIgnoreCase(response.firstName())
                || !lead.lastName().equalsIgnoreCase(response.lastName())) {
            detail.append("Name mismatch: expected '")
                    .append(lead.firstName()).append(" ").append(lead.lastName())
                    .append("', got '")
                    .append(response.firstName()).append(" ").append(response.lastName())
                    .append("'");
        }
        if (!lead.birthdate().equals(LocalDate.parse(response.birthdate()))) {
            if (detail.length() > 0) {
                detail.append("; ");
            }
            detail.append("Birthdate mismatch: expected '")
                    .append(lead.birthdate())
                    .append("', got '")
                    .append(response.birthdate())
                    .append("'");
        }
        return detail.toString();
    }
}
