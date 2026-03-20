package com.crm.qualifier.adapter.inbound.cli.mapper;

import com.crm.qualifier.adapter.inbound.cli.dto.LeadRequest;
import com.crm.qualifier.domain.model.Lead;

import java.time.LocalDate;

public class LeadRequestMapper {

    public Lead toDomain(LeadRequest request) {
        LocalDate birthdate = LocalDate.parse(request.birthdate());
        return new Lead(
                request.nationalId(),
                birthdate,
                request.firstName(),
                request.lastName(),
                request.email()
        );
    }
}
