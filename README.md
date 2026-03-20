# Sales Pipeline - Automated Lead Qualification

A **spec-driven, hexagonal architecture** application that automates the lead qualification process through a multi-step validation pipeline. Built with **Java 17** and **Spring Boot 3.2.x** as a CLI application.

## Overview

The system takes a sales lead (person data) and runs it through a series of validation steps:

1. **National Registry Verification** - Confirms identity against a government registry
2. **Judicial Records Check** - Screens for judicial history
3. **Compliance Bureau (OFAC/Sanctions)** - Checks sanctions lists with cache-aside pattern
4. **Qualification Score** - Generates and evaluates a qualification score

The result is one of: **APPROVED** (lead becomes a prospect), **REJECTED** (validation failed), or **MANUAL_REVIEW** (compliance bureau unavailable).

## Architecture

### Hexagonal Architecture (Ports & Adapters)

```
+-------------------------------------------------------------+
|                    INFRASTRUCTURE (Adapters)                  |
|  +--------------------------------------------------------+  |
|  |                 APPLICATION (Use Cases)                  |  |
|  |  +--------------------------------------------------+  |  |
|  |  |                 DOMAIN (Core)                      |  |  |
|  |  |                                                    |  |  |
|  |  |  Lead, Prospect, ValidationResult,                |  |  |
|  |  |  PipelineResult, QualificationStatus              |  |  |
|  |  |                                                    |  |  |
|  |  +--------------------------------------------------+  |  |
|  |                                                         |  |
|  |  Ports (interfaces):                                    |  |
|  |    Inbound:  QualifyLeadUseCase                        |  |
|  |    Outbound: RegistryPort, JudicialPort,               |  |
|  |              ComplianceBureauPort, ScorePort,           |  |
|  |              ComplianceCachePort                        |  |
|  |                                                         |  |
|  |  Services:                                              |  |
|  |    LeadQualificationService (orchestrator)              |  |
|  |                                                         |  |
|  +--------------------------------------------------------+  |
|                                                               |
|  Inbound Adapters:          Outbound Adapters:                |
|    CliAdapter                 SimulatedRegistryAdapter         |
|                               SimulatedJudicialAdapter        |
|                               SimulatedComplianceBureauAdapter|
|                               RandomScoreAdapter              |
|                               FileComplianceCacheAdapter      |
|                                                               |
+-------------------------------------------------------------+
```

**Dependency Rule:** Source code dependencies point **inward only**. Domain has zero dependencies. Application depends only on Domain. Adapters depend on Application and Domain. Spring configuration (`config/`) is the only package with framework annotations.

### Pipeline Flow

```
         +---------------------+
         |       Lead          |
         +---------+-----------+
                   |
          +--------+--------+
          |                 |
          v                 v
  +---------------+ +---------------+
  |  Step 1a:     | |  Step 1b:     |    PARALLEL
  |  Registry     | |  Judicial     |    (CompletableFuture)
  |  Validation   | |  Records      |
  +-------+-------+ +-------+-------+
          |                 |
          |   Both must     |
          |    pass         |
          +--------+--------+
                   |
                   v
          +---------------+
          |  Step 2:      |
          |  Compliance   |    SEQUENTIAL + CACHED + RESILIENT
          |  Bureau       |
          +-------+-------+
                  |
                  v
          +---------------+
          |  Step 3:      |
          |  Qualification|    SEQUENTIAL
          |  Score        |
          +-------+-------+
                  |
                  v
         +---------------------+
         |  PipelineResult     |
         |  (APPROVED /        |
         |   REJECTED /        |
         |   MANUAL_REVIEW)    |
         +---------------------+
```

### DTO Boundary Diagram

Domain objects **never cross the adapter boundary**. Every piece of data entering or leaving the application passes through a DTO + mapper:

```
  CLI args -----> LeadRequest (DTO) ---> LeadRequestMapper ---> Lead (Domain)
                                                                     |
                              Service orchestrates via ports          |
                                                                     v
    RegistryPort <--- SimulatedRegistryAdapter <--- RegistryApiResponse (DTO)
                       +-- RegistryMapper converts to domain
    JudicialPort <--- SimulatedJudicialAdapter <--- JudicialApiResponse (DTO)
                       +-- JudicialMapper converts to domain
    ComplianceBureauPort <--- SimulatedComplianceAdapter <--- ComplianceApiResponse (DTO)
                               +-- ComplianceMapper converts to domain
    ComplianceCachePort <--- FileComplianceCacheAdapter <--- CacheEntryDto (DTO)
                              +-- CacheMapper converts to domain
                                                                     |
                                                                     v
                                                            PipelineResult (Domain)
                                                                     |
                        QualificationResponseMapper                  v
                                                      QualificationResponse (DTO)
                                                                     |
                                                                     v
                                                            CLI output (formatted)
```

## Build

```bash
mvn clean package
```

## Run

```bash
java -jar target/sales-pipeline.jar \
  --nationalId=ABC123456 \
  --birthdate=1990-01-15 \
  --firstName=John \
  --lastName=Doe \
  --email=john@example.com
```

### Sample Output

```
[PIPELINE] Starting qualification for nationalId=ABC123456
[REGISTRY] Checking nationalId=ABC123456... MATCH (523ms)
[JUDICIAL] Checking nationalId=ABC123456... CLEAN (847ms)
[COMPLIANCE] Cache MISS - calling external service
[COMPLIANCE] Service call for nationalId=ABC123456... CLEAR (234ms)
[SCORE] Generated score: 78/100 (threshold: >60)
[PIPELINE] Qualification APPROVED - lead converted to prospect

====================================================
  QUALIFICATION RESULT
====================================================
  National ID : ABC123456
  Full Name   : John Doe
  Status      : APPROVED
  Score       : 78/100
----------------------------------------------------
  VALIDATION STEPS:
    [PASS] NationalRegistry: Person verified in national registry
    [PASS] JudicialRecords: No judicial records found
    [PASS] ComplianceBureau: Not found in sanctions list
    [PASS] QualificationScore: Score: 78/100 (threshold: >60)
----------------------------------------------------
  Lead qualified successfully - converted to prospect.
====================================================
```

## Test

```bash
# All tests (114 tests)
mvn test

# Unit tests only (fast, < 2 seconds)
mvn test -Dgroups=unit

# Integration tests only
mvn test -Dgroups=integration
```

### Test Summary

| Category | Test Count | Description |
|----------|-----------|-------------|
| Domain Model | 33 | Lead, Prospect, ValidationResult, PipelineResult validations |
| Application Service | 17 | Orchestration logic with inline stubs (most important) |
| Mappers | 19 | DTO-to-domain and domain-to-DTO conversions |
| Adapters | 16 | Simulated services with latency, seeds, I/O |
| Integration | 11 | Full pipeline with real adapters and file cache |
| Spring Config | 8 | Context loading, property binding, bean wiring |
| **Total** | **114** | |

## Spring Boot Configuration

### `application.yml`

```yaml
cache:
  file-path: ./data/compliance-cache.json   # Compliance cache file location
  ttl-hours: 24                              # Cache TTL in hours

adapter:
  registry:
    seed:            # null = random, set a long for deterministic behavior
  judicial:
    seed:
  compliance:
    seed:
  score:
    seed:

pipeline:
  parallel-timeout-seconds: 10   # Global timeout for parallel phase
  per-service-timeout-seconds: 5  # Per-service timeout
  score-threshold: 60             # Score must be strictly greater than this

spring:
  main:
    web-application-type: none   # CLI app, no web server
    banner-mode: off             # Clean CLI output
```

### Composition Root

Spring annotations exist **only** in the `config/` package and `Application.java`:

| Package | Spring annotations? | Reason |
|---------|:-------------------:|--------|
| `domain.*` | No | Pure business objects, zero dependencies |
| `application.*` | No | Use cases and port interfaces, plain Java |
| `adapter.*` | No | Adapters are POJOs; Spring wires them from outside |
| `config.*` | Yes | `@Configuration`, `@Bean`, `@Value` |
| `Application.java` | Yes | `@SpringBootApplication`, `CommandLineRunner` |

## Project Structure

```
com.crm.qualifier/
|
+-- domain/                          # INNER RING - Pure business objects
|   +-- model/
|   |   +-- Lead.java                # Value Object (record)
|   |   +-- Prospect.java            # Value Object (record)
|   |   +-- ValidationResult.java    # Value Object (record)
|   |   +-- PipelineResult.java      # Value Object (record)
|   |   +-- QualificationStatus.java # Enum
|   |   +-- RegistryCheckResult.java # Value Object with nested enum
|   |   +-- JudicialCheckResult.java # Value Object with nested enum
|   |   +-- ComplianceCheckResult.java # Value Object with nested enum
|   |
|   +-- exception/
|       +-- InvalidLeadException.java
|       +-- QualificationException.java
|       +-- ComplianceBureauUnavailableException.java  # Checked exception
|
+-- application/                     # MIDDLE RING - Use cases & ports
|   +-- port/
|   |   +-- inbound/
|   |   |   +-- QualifyLeadUseCase.java
|   |   +-- outbound/
|   |       +-- RegistryPort.java
|   |       +-- JudicialPort.java
|   |       +-- ComplianceBureauPort.java
|   |       +-- QualificationScorePort.java
|   |       +-- ComplianceCachePort.java
|   +-- service/
|       +-- LeadQualificationService.java
|
+-- adapter/                         # OUTER RING - Infrastructure
|   +-- inbound/
|   |   +-- cli/
|   |       +-- CliAdapter.java
|   |       +-- LeadRequest.java                  # Inbound DTO
|   |       +-- LeadRequestMapper.java
|   |       +-- QualificationResponse.java        # Outbound DTO
|   |       +-- QualificationResponseMapper.java
|   +-- outbound/
|       +-- registry/
|       |   +-- SimulatedRegistryAdapter.java
|       |   +-- RegistryApiResponse.java          # External API DTO
|       |   +-- RegistryMapper.java
|       +-- judicial/
|       |   +-- SimulatedJudicialAdapter.java
|       |   +-- JudicialApiResponse.java
|       |   +-- JudicialMapper.java
|       +-- compliance/
|       |   +-- SimulatedComplianceBureauAdapter.java
|       |   +-- ComplianceApiResponse.java
|       |   +-- ComplianceMapper.java
|       +-- score/
|       |   +-- RandomScoreAdapter.java
|       +-- cache/
|           +-- FileComplianceCacheAdapter.java
|           +-- CacheEntryDto.java
|           +-- CacheMapper.java
|
+-- config/                          # SPRING CONFIGURATION
|   +-- AdapterConfig.java           # @Bean for outbound adapters
|   +-- ServiceConfig.java           # @Bean for service and CLI adapter
|
+-- Application.java                 # @SpringBootApplication entry point
```

## Design Decisions

### Why Hexagonal Architecture?
- **Testability**: Domain and business logic testable without infrastructure. The 17 service tests use inline stubs with zero mocking frameworks.
- **Flexibility**: Swapping from simulated to real HTTP services requires only a new adapter class.
- **Clean boundaries**: No Spring annotations leak into domain or application code.

### Why DTOs at Every Boundary?
- External API format changes only affect the DTO + mapper in the adapter package.
- Domain model changes only affect mappers, not adapters.
- Gson/Jackson never touches domain records directly.

### Why No Mocking Frameworks?
- Port interfaces are designed for easy stubbing (single-method interfaces).
- Inline stubs (lambdas, anonymous classes) are more readable and debuggable.
- Tests are framework-free and explicit about behavior.

### Why `CompletableFuture` for Parallelism?
- Registry and Judicial checks are independent and take 200-1000ms each.
- Running them in parallel saves ~500ms on average.
- `CompletableFuture.supplyAsync()` with the common ForkJoinPool is appropriate for 2 tasks in a CLI app.

### Why Checked Exception for Compliance Bureau?
- `ComplianceBureauUnavailableException extends Exception` forces the service layer to explicitly handle the unavailability case.
- This prevents accidentally crashing the pipeline when the bureau is down.
- The MANUAL_REVIEW path is a deliberate business decision, not a bug.

### Why Cache-Aside Pattern?
- Compliance bureau calls are expensive and rate-limited in production.
- Cache is an optimization, not a correctness requirement -- cache failures degrade gracefully to a miss.
- File-based cache persists across JVM restarts for the CLI use case.
