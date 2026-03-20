package com.crm.qualifier.config;

import com.crm.qualifier.adapter.inbound.cli.CliAdapter;
import com.crm.qualifier.application.port.inbound.QualifyLeadUseCase;
import com.crm.qualifier.application.port.outbound.*;
import com.crm.qualifier.application.service.LeadQualificationService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(args = {})
@Tag("integration")
class SpringContextTest {

    @Autowired
    private QualifyLeadUseCase qualifyLeadUseCase;

    @Autowired
    private RegistryPort registryPort;

    @Autowired
    private JudicialPort judicialPort;

    @Autowired
    private ComplianceBureauPort complianceBureauPort;

    @Autowired
    private QualificationScorePort qualificationScorePort;

    @Autowired
    private ComplianceCachePort complianceCachePort;

    @Autowired
    private CliAdapter cliAdapter;

    @Test
    void contextLoads() {
        // If we get here, the context loaded successfully
    }

    @Test
    void allPortsAreWired() {
        assertNotNull(registryPort);
        assertNotNull(judicialPort);
        assertNotNull(complianceBureauPort);
        assertNotNull(qualificationScorePort);
        assertNotNull(complianceCachePort);
    }

    @Test
    void qualifyLeadUseCaseIsWired() {
        assertNotNull(qualifyLeadUseCase);
        assertInstanceOf(LeadQualificationService.class, qualifyLeadUseCase);
    }

    @Test
    void cliAdapterIsWired() {
        assertNotNull(cliAdapter);
    }
}
