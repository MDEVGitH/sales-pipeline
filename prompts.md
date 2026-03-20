# Session Prompts — Sales Pipeline (Spec-Driven Development)

## Prompt 1: Business Context

**User:**
> In our company, we utilize a custom-made CRM to manage our sales pipeline where leads convert into prospects. To achieve our goals, we are automating the manual checks currently performed by sales agents.
> Every lead in the CRM contains basic personal information: national identification number, birthdate, first name, last name, and email. Your mission is to build the automated orchestration layer that validates these leads before they are moved to the "Prospect" stage.

---

## Prompt 2: Business Logic & Technical Constraints

**User:**
> Business logic: Automated Lead Qualification
> The criteria to turn a sales-qualified lead into a prospect involves passing three distinct validations:
> - National Registry Validation: The person must exist in the external registry, and their data must match our local database.
> - Judicial Records Check: The person must have no records in the national archives' external system.
> - Compliance Bureau (OFAC/Sanctions) & Caching: A lightweight check against a compliance bureau. To optimize external calls and handle latency, implement a simple Persistent/Durable mechanism for these bureau responses. If the service is down, demonstrate resilience by gracefully handling the failure or triggering a manual review flow.
> - Prospect Qualification Score: An internal system provides a random score between 0 and 100. A lead is converted only if the score is greater than 60.
>
> Technical Execution Constraints
> - Parallelism: The first two validations (Registry and Judicial) are non-dependent and must execute in parallel.
> - Compliance Bureau: Requires the successful output of the previous two validations to execute.
> - Sequential Dependency: The Qualification Score requires the successful clean output of the previous Compliance Bureau to execute.

---

## Prompt 3: Technical Considerations

**User:**
> Technical Considerations
> - Language: Java (JVM).
> - Interface: A simple CLI is sufficient; do not build a UI or client-server solution.
> - Spec-Driven Development: Before implementing features, create a brief specification document that defines what you're building, key requirements, and design decisions. Your git history will show specs created before implementation.
> - Robust Process Documentation: Include a comprehensive README that describes the entire process you took to solve the challenge.
> - AI Artifacts Required: Submit all AI conversation history, complete prompt logs, and any context files used alongside your code submission.
> - Infrastructure: Do not use external databases or message queues.
> - External Systems: Implement these as functions that respond with success or failure. You must simulate latency for these requests. You may use HTTP stubs or any technique of your choice.

**Claude Response:**
Created a comprehensive implementation plan with 10 steps:
1. Initialize project (Maven, Java 17, JUnit 5, Gson)
2. Write spec documents (4 specs committed before code)
3. Implement domain model (Lead, Prospect, ValidationResult, PipelineResult records)
4. Implement external services (simulated with Thread.sleep latency)
5. Implement cache (file-based JSON, TTL 24h)
6. Implement validators
7. Implement orchestrator (CompletableFuture for parallel execution)
8. Implement CLI
9. Write tests
10. README

Pipeline diagram:
```
Lead ──┬── 1a. Registry Validation ──┐
       │       (parallel)            ├── 2. Compliance Bureau ── 3. Score > 60 ── Prospect
       └── 1b. Judicial Records ─────┘      (cached/resilient)
```

---

## Prompt 4: Iteration Request — Enhanced Specs

**User:**
> yo creo que es un muy buen comienzo, vamos a iterarlo ahora en la carpeta sales-pipeline, solo quiero que creemos un spec super detallado, agregale al spec detalle tecnico (arquitectura hexagonal, codigo limpio) y otro detalle de los tests (unitarios y de integración de ser necesario)

**Claude Response:**
Created 6 detailed spec files in `sales-pipeline/specs/`:

### `01-domain-model.md`
- Lead, Prospect, ValidationResult, PipelineResult as Java records
- Field constraints with types, ranges, regex patterns
- Factory methods (`Prospect.fromLead()`, `ValidationResult.pass()/fail()`)
- Domain rules table (DR-1 through DR-6)
- Design rationale: Value Objects, immutability by construction

### `02-architecture.md`
- Hexagonal Architecture (Ports & Adapters) with ring diagram
- Dependency Rule table (inner layers never depend on outer)
- Full package structure: domain/ → application/ (ports, service) → adapter/ (inbound/outbound)
- SOLID principles mapped to specific classes
- Clean Code rules: meaningful names, small methods, no side effects, fail fast, CQS
- Composition Root with wiring diagram
- Data flow diagram
- Error handling strategy table

### `03-validation-pipeline.md`
- Pipeline topology diagram (parallel → sequential → sequential)
- Each step with: port interface signature, contract table, domain value objects, success criteria, mapping to ValidationResult
- Short-circuit rules table
- Output scenarios table (7 scenarios with expected # results, status, prospect)

### `04-external-services.md`
- Port → Adapter mapping table
- Each adapter: behavior pseudocode, latency range, response distribution percentages
- Deterministic mode for testing (seed parameter)
- Cache adapter: get/put flow, configuration, file format, error handling, thread safety
- Latency summary table
- Observability logging format

### `05-orchestration-and-resilience.md`
- QualifyLeadUseCase inbound port contract
- Full pseudocode for `qualify()` method
- Private helper methods with logic
- Sealed interface `ComplianceOutcome` (Clear, Flagged, Unavailable)
- Parallelism justification table (why CompletableFuture vs alternatives)
- Timeout strategy table
- Resilience patterns: graceful degradation, cache-aside, short-circuit (with diagrams)
- Logging contract with format examples

### `06-testing-strategy.md`
- Test Pyramid diagram (unit 40+ / integration 8-12 / e2e 1-2)
- Testing principles table
- Every test class with individual test cases in tables:
  - Domain: LeadTest (16), ProspectTest (6), ValidationResultTest (5), PipelineResultTest (6)
  - Service: LeadQualificationServiceTest (18 tests covering all paths)
  - Adapters: per-adapter test tables
  - Cache: 10 tests with @TempDir
- Integration tests: 11 tests across 2 files
- E2E tests: CLI output verification
- Test organization tree
- Test double strategy: inline stubs implementing port interfaces (no mocking frameworks)

---

## Prompt 5: Add DTOs to Architecture

**User:**
> Iterate the architecture file to use dtos instead of domain entities in adapter layer

**Claude Response:**
Updated `02-architecture.md` with new "DTO Strategy (Anti-Corruption Layer)" section:
- **Why DTOs:** isolation from external data representations, dependency rule protection
- **DTO Rules table:** live in adapter only, plain data carriers, mappers convert DTO ↔ Domain
- **Inbound DTOs:** `LeadRequest` (raw strings) → `LeadRequestMapper` → `Lead` (domain); `PipelineResult` → `QualificationResponseMapper` → `QualificationResponse`
- **Outbound DTOs:** `RegistryApiResponse`, `JudicialApiResponse`, `ComplianceApiResponse` with mappers
- **Cache DTOs:** `CacheEntryDto` + `CacheMapper` (Gson never touches domain objects)
- **DTO boundary diagram** showing conversion at every adapter entry/exit point
- Key rule: "domain objects never cross the adapter boundary"

Updated `04-external-services.md`:
- Each adapter now documents its DTO, mapper, and internal flow (simulate → build DTO → mapper.toDomain → return domain type)

Updated `06-testing-strategy.md`:
- Added mapper test section (RegistryMapperTest 5 tests, JudicialMapperTest 2, ComplianceMapperTest 4, CacheMapperTest 5, LeadRequestMapperTest 3, QualificationResponseMapperTest 4)
- Updated test organization tree with mapper test files

---

## Prompt 6: Add Spring Boot for Composition Root

**User:**
> Quiero que el Composition Root (Dependency Wiring) sea manejado en un archivo de config, puedes usar spring-boot en el proyecto, asegurate de agregar esto al spec

**Claude Response:**
Updated `02-architecture.md` — replaced manual `Main.java` composition root with Spring Boot:
- **Why Spring Boot table:** config changes without recompile, adapter swapping via `@ConditionalOnProperty`, test overrides with `@TestConfiguration`
- **Why NOT `@Component`/`@Service`:** domain and application must stay framework-agnostic
- **`Application.java`:** `@SpringBootApplication` + `CommandLineRunner` (CLI, no web server)
- **`AdapterConfig.java`:** `@Bean` for each port with `@Value` for seeds and cache config
- **`ServiceConfig.java`:** `@Bean` for `QualifyLeadUseCase` and `CliAdapter`
- **`application.yml`:** externalized config for cache, adapters, pipeline, Spring settings
- **Adapter swapping example:** `@ConditionalOnProperty(name = "adapter.registry.type")` to toggle simulated vs HTTP
- **Framework-agnostic table:** which packages have Spring annotations (only `config/` and `Application.java`)
- **Wiring diagram:** Spring IoC Container → AdapterConfig → ServiceConfig → Application

Updated `06-testing-strategy.md`:
- Integration tests remain **plain Java** (no `@SpringBootTest`) for speed
- Added Spring Configuration Tests: `SpringContextTest` (context loads, all ports wired), `ConfigurationPropertiesTest` (defaults, overrides)
- Test `application.yml` with deterministic seeds
- E2E tests use `@SpringBootTest`

---

## Prompt 7: Execute Implementation

**User:**
> Lo veo bien, ejecuta la implementación como si fueras un ingeniero senior

**Claude Response:**
Launched senior engineer agent that:
1. Read all 6 specs
2. Initialized project with Git + GitHub repo (MDEVGitH/sales-pipeline)
3. Committed specs before code
4. Implemented in order: domain → application → adapters (DTOs + mappers) → Spring config → tests → README
5. Result: https://github.com/MDEVGitH/sales-pipeline
   - 7 atomic commits, all pushed
   - 114 tests, all passing
   - Full hexagonal architecture with DTOs, Spring Boot config, and comprehensive test suite
