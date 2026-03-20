package com.crm.qualifier.adapter.outbound.cache;

import com.crm.qualifier.domain.model.ComplianceCheckResult;
import com.crm.qualifier.domain.model.ComplianceCheckResult.ComplianceStatus;

import java.time.LocalDateTime;

public class CacheMapper {

    public CacheEntryDto toDto(ComplianceCheckResult result) {
        CacheEntryDto dto = new CacheEntryDto();
        dto.status = result.status().name();
        dto.timestamp = LocalDateTime.now().toString();
        return dto;
    }

    public ComplianceCheckResult toDomain(CacheEntryDto dto) {
        ComplianceStatus status = ComplianceStatus.valueOf(dto.status);
        return new ComplianceCheckResult(status);
    }

    public boolean isExpired(CacheEntryDto dto, long ttlHours) {
        LocalDateTime cached = LocalDateTime.parse(dto.timestamp);
        return cached.plusHours(ttlHours).isBefore(LocalDateTime.now());
    }
}
