package com.crm.qualifier.adapter.inbound.cli.dto;

public record LeadRequest(
        String nationalId,
        String birthdate,
        String firstName,
        String lastName,
        String email
) {}
