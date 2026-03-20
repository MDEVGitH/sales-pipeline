package com.crm.qualifier.adapter.outbound.registry;

public record RegistryApiResponse(
        boolean found,
        String firstName,
        String lastName,
        String birthdate,
        double matchScore
) {}
