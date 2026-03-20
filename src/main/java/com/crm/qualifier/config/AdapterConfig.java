package com.crm.qualifier.config;

import com.crm.qualifier.adapter.outbound.cache.FileComplianceCacheAdapter;
import com.crm.qualifier.adapter.outbound.compliance.SimulatedComplianceBureauAdapter;
import com.crm.qualifier.adapter.outbound.judicial.SimulatedJudicialAdapter;
import com.crm.qualifier.adapter.outbound.registry.SimulatedRegistryAdapter;
import com.crm.qualifier.adapter.outbound.score.RandomScoreAdapter;
import com.crm.qualifier.application.port.outbound.ComplianceBureauPort;
import com.crm.qualifier.application.port.outbound.ComplianceCachePort;
import com.crm.qualifier.application.port.outbound.JudicialPort;
import com.crm.qualifier.application.port.outbound.QualificationScorePort;
import com.crm.qualifier.application.port.outbound.RegistryPort;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AdapterConfig {

    @Bean
    public RegistryPort registryPort(
            @Value("${adapter.registry.seed:#{null}}") Long seed) {
        return seed != null
                ? new SimulatedRegistryAdapter(seed)
                : new SimulatedRegistryAdapter();
    }

    @Bean
    public JudicialPort judicialPort(
            @Value("${adapter.judicial.seed:#{null}}") Long seed) {
        return seed != null
                ? new SimulatedJudicialAdapter(seed)
                : new SimulatedJudicialAdapter();
    }

    @Bean
    public ComplianceBureauPort complianceBureauPort(
            @Value("${adapter.compliance.seed:#{null}}") Long seed) {
        return seed != null
                ? new SimulatedComplianceBureauAdapter(seed)
                : new SimulatedComplianceBureauAdapter();
    }

    @Bean
    public QualificationScorePort qualificationScorePort(
            @Value("${adapter.score.seed:#{null}}") Long seed) {
        return seed != null
                ? new RandomScoreAdapter(seed)
                : new RandomScoreAdapter();
    }

    @Bean
    public ComplianceCachePort complianceCachePort(
            @Value("${cache.file-path:./data/compliance-cache.json}") String filePath,
            @Value("${cache.ttl-hours:24}") long ttlHours) {
        return new FileComplianceCacheAdapter(filePath, ttlHours);
    }
}
