package com.crm.qualifier.config;

import com.crm.qualifier.adapter.inbound.cli.CliAdapter;
import com.crm.qualifier.application.port.inbound.QualifyLeadUseCase;
import com.crm.qualifier.application.port.outbound.ComplianceBureauPort;
import com.crm.qualifier.application.port.outbound.ComplianceCachePort;
import com.crm.qualifier.application.port.outbound.JudicialPort;
import com.crm.qualifier.application.port.outbound.QualificationScorePort;
import com.crm.qualifier.application.port.outbound.RegistryPort;
import com.crm.qualifier.application.service.LeadQualificationService;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ServiceConfig {

    @Bean
    public QualifyLeadUseCase qualifyLeadUseCase(
            RegistryPort registryPort,
            JudicialPort judicialPort,
            ComplianceBureauPort complianceBureauPort,
            QualificationScorePort qualificationScorePort,
            ComplianceCachePort complianceCachePort) {
        return new LeadQualificationService(
                registryPort, judicialPort, complianceBureauPort,
                qualificationScorePort, complianceCachePort
        );
    }

    @Bean
    public CliAdapter cliAdapter(QualifyLeadUseCase qualifyLeadUseCase) {
        return new CliAdapter(qualifyLeadUseCase);
    }
}
