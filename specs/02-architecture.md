# 02 — Architecture Specification

## Architectural Style: Hexagonal Architecture (Ports & Adapters)

The system follows **Hexagonal Architecture** (Alistair Cockburn) to achieve:
- **Testability**: The domain and business logic are testable without any infrastructure.
- **Flexibility**: External systems (registry, judicial, compliance) can be swapped without touching business logic.
- **Clean boundaries**: Dependencies point **inward** — infrastructure depends on domain, never the reverse.

---

## Layered Ring Model

```
┌─────────────────────────────────────────────────────────────┐
│                    INFRASTRUCTURE (Adapters)                 │
│  ┌────────────────────────────────────────────────────────┐  │
│  │                 APPLICATION (Use Cases)                 │  │
│  │  ┌──────────────────────────────────────────────────┐  │  │
│  │  │                 DOMAIN (Core)                     │  │  │
│  │  │                                                   │  │  │
│  │  │  Lead, Prospect, ValidationResult,               │  │  │
│  │  │  PipelineResult, QualificationStatus             │  │  │
│  │  │                                                   │  │  │
│  │  └──────────────────────────────────────────────────┘  │  │
│  │                                                        │  │
│  │  Ports (interfaces):                                   │  │
│  │    Inbound:  QualifyLeadUseCase                       │  │
│  │    Outbound: RegistryPort, JudicialPort,              │  │
│  │              ComplianceBureauPort, ScorePort,          │  │
│  │              ComplianceCachePort                       │  │
│  │                                                        │  │
│  │  Services:                                             │  │
│  │    LeadQualificationService (orchestrator)             │  │
│  │                                                        │  │
│  └────────────────────────────────────────────────────────┘  │
│                                                              │
│  Inbound Adapters:          Outbound Adapters:               │
│    CliAdapter                 SimulatedRegistryAdapter        │
│                               SimulatedJudicialAdapter       │
│                               SimulatedComplianceBureauAdapter│
│                               RandomScoreAdapter             │
│                               FileComplianceCacheAdapter     │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

---

## Dependency Rule

> Source code dependencies must point **inward only**. Nothing in an inner ring can know anything about something in an outer ring.

| Layer            | Can depend on         | Cannot depend on     |
|------------------|-----------------------|----------------------|
| Domain           | Nothing (zero deps)   | Application, Infra   |
| Application      | Domain                | Infrastructure       |
| Infrastructure   | Application, Domain   | —                    |

**Enforcement:** No `import` from `adapter.*` or `infrastructure.*` packages shall appear in `domain.*` or `application.*` packages.

---

## Package Structure

```
com.crm.qualifier/
│
├── domain/                          # INNER RING — Pure business objects
│   ├── model/
│   │   ├── Lead.java                # Value Object (record)
│   │   ├── Prospect.java            # Value Object (record)
│   │   ├── ValidationResult.java    # Value Object (record)
│   │   ├── PipelineResult.java      # Value Object (record)
│   │   └── QualificationStatus.java # Enum
│   │
│   └── exception/
│       ├── InvalidLeadException.java        # Thrown when Lead construction fails
│       └── QualificationException.java      # Thrown for unexpected pipeline errors
│
├── application/                     # MIDDLE RING — Use cases & ports
│   ├── port/
│   │   ├── inbound/
│   │   │   └── QualifyLeadUseCase.java          # Inbound port (interface)
│   │   │
│   │   └── outbound/
│   │       ├── RegistryPort.java                # Outbound port (interface)
│   │       ├── JudicialPort.java                # Outbound port (interface)
│   │       ├── ComplianceBureauPort.java        # Outbound port (interface)
│   │       ├── QualificationScorePort.java      # Outbound port (interface)
│   │       └── ComplianceCachePort.java         # Outbound port (interface)
│   │
│   └── service/
│       └── LeadQualificationService.java        # Implements QualifyLeadUseCase
│                                                 # Orchestrates the pipeline
│                                                 # Depends ONLY on ports (interfaces)
│
├── adapter/                         # OUTER RING — Infrastructure implementations
│   ├── inbound/
│   │   └── cli/
│   │       ├── CliAdapter.java                  # CLI entry point, parses args
│   │       ├── LeadRequest.java                 # Inbound DTO: raw CLI input
│   │       ├── LeadRequestMapper.java           # Maps LeadRequest → Lead (domain)
│   │       ├── QualificationResponse.java       # Outbound DTO: formatted result
│   │       └── QualificationResponseMapper.java # Maps PipelineResult → QualificationResponse
│   │
│   └── outbound/
│       ├── registry/
│       │   ├── SimulatedRegistryAdapter.java    # Implements RegistryPort
│       │   ├── RegistryApiResponse.java         # External API DTO (simulated)
│       │   └── RegistryMapper.java              # Maps RegistryApiResponse → RegistryCheckResult (domain)
│       │
│       ├── judicial/
│       │   ├── SimulatedJudicialAdapter.java    # Implements JudicialPort
│       │   ├── JudicialApiResponse.java         # External API DTO (simulated)
│       │   └── JudicialMapper.java              # Maps JudicialApiResponse → JudicialCheckResult (domain)
│       │
│       ├── compliance/
│       │   ├── SimulatedComplianceBureauAdapter.java  # Implements ComplianceBureauPort
│       │   ├── ComplianceApiResponse.java             # External API DTO (simulated)
│       │   └── ComplianceMapper.java                  # Maps ComplianceApiResponse → ComplianceCheckResult (domain)
│       │
│       ├── score/
│       │   └── RandomScoreAdapter.java          # Implements QualificationScorePort
│       │
│       └── cache/
│           ├── FileComplianceCacheAdapter.java  # Implements ComplianceCachePort
│           ├── CacheEntryDto.java               # JSON serialization DTO for cache file
│           └── CacheMapper.java                 # Maps CacheEntryDto ↔ ComplianceCheckResult (domain)
│
├── config/                          # SPRING CONFIGURATION — Composition Root
│   ├── AdapterConfig.java           # @Configuration: wires outbound adapters as @Bean
│   ├── ServiceConfig.java           # @Configuration: wires application services as @Bean
│   └── CacheConfig.java             # @Configuration: cache-specific settings (path, TTL)
│
├── Application.java                 # @SpringBootApplication entry point
│
└── src/main/resources/
    └── application.yml              # Externalized configuration (cache path, TTL, timeouts, seeds)
```

---

## Clean Code Principles Applied

### 1. Single Responsibility Principle (SRP)
Each class has exactly **one reason to change**:
- `Lead` → changes only if lead data structure changes
- `LeadQualificationService` → changes only if pipeline orchestration logic changes
- `SimulatedRegistryAdapter` → changes only if the registry simulation changes
- `FileComplianceCacheAdapter` → changes only if the caching mechanism changes

### 2. Open/Closed Principle (OCP)
- Adding a new external validation (e.g., credit score) requires:
  1. A new outbound port interface
  2. A new adapter implementing it
  3. Injecting it into `LeadQualificationService`
- **No existing code is modified** — the service accepts ports via constructor injection.

### 3. Liskov Substitution Principle (LSP)
- Any implementation of `RegistryPort` (simulated, HTTP-based, mock) can replace another without breaking the service.
- Tests use test doubles that implement the same port interfaces.

### 4. Interface Segregation Principle (ISP)
- Each port has a **single method** — callers are not forced to depend on methods they don't use.
- `RegistryPort` has only `check()`, `ComplianceCachePort` has only `get()` and `put()`.

### 5. Dependency Inversion Principle (DIP)
- `LeadQualificationService` depends on **port interfaces**, not on concrete adapters.
- Concrete adapters are injected by Spring's IoC container via `@Configuration` classes.

### 6. Additional Clean Code Rules

| Rule | Application |
|------|-------------|
| **Meaningful names** | `QualifyLeadUseCase` not `IService`, `RegistryPort` not `IRegistryGateway` |
| **Small methods** | Each method does one thing; max ~20 lines |
| **No side effects** | Domain objects are pure; only adapters have side effects |
| **Fail fast** | Invalid state rejected at construction time |
| **Immutability** | All domain objects are Java records (immutable by default) |
| **No null returns** | Use `Optional<T>` where absence is valid |
| **Composition over inheritance** | No class hierarchies; ports are composed via injection |
| **Command-Query Separation** | Methods either return data or perform actions, not both |

---

## Composition Root (Dependency Wiring via Spring Boot)

The composition root is managed by **Spring Boot's IoC container** through explicit `@Configuration` classes. We use **Java-based configuration** (not component scanning with `@Service`/`@Component`) to keep the wiring explicit and visible in one place.

### Why Spring Boot?

| Concern | Without Spring | With Spring Boot |
|---------|---------------|------------------|
| Wiring changes | Edit `Main.java`, recompile | Edit `application.yml`, no recompile |
| Configuration values | Hardcoded or CLI args | `application.yml` with profiles |
| Swapping adapters | Change constructor calls in code | Change which `@Bean` is active (via `@Profile` or `@ConditionalOnProperty`) |
| Testing overrides | Manual wiring in each test | `@TestConfiguration` or `@MockBean` |

### Why NOT `@Component` / `@Service` scanning?

Domain and application layers must remain **framework-agnostic**. No Spring annotations in `domain.*` or `application.*` packages. The `@Configuration` classes in `config/` are the only Spring-aware code — they live in the infrastructure ring and explicitly wire everything.

### `Application.java` (Entry Point)

```java
@SpringBootApplication
public class Application implements CommandLineRunner {

    private final CliAdapter cliAdapter;

    public Application(CliAdapter cliAdapter) {
        this.cliAdapter = cliAdapter;
    }

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }

    @Override
    public void run(String... args) {
        cliAdapter.run(args);
    }
}
```

- Implements `CommandLineRunner` — Spring Boot runs it as a CLI app, not a web server.
- No web dependencies — use `spring-boot-starter` only (not `spring-boot-starter-web`).

### `AdapterConfig.java` (Outbound Adapter Beans)

```java
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
```

**Key design:**
- Beans are declared by **port interface type** (e.g., `RegistryPort`), not by concrete class.
- Spring injects them anywhere a port is required — the consumer never knows the concrete type.
- Optional `seed` values allow deterministic behavior for testing/demos via config.

### `ServiceConfig.java` (Application Service Beans)

```java
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
```

**Note:** `LeadQualificationService` has **no Spring annotations** — it's a plain Java class. Spring only knows about it through this config class.

### `application.yml` (Externalized Configuration)

```yaml
# ── Cache Configuration ──
cache:
  file-path: ./data/compliance-cache.json
  ttl-hours: 24

# ── Adapter Configuration ──
adapter:
  registry:
    seed:             # null = random (production-like), set a long for deterministic
  judicial:
    seed:
  compliance:
    seed:
  score:
    seed:

# ── Pipeline Configuration ──
pipeline:
  parallel-timeout-seconds: 10
  per-service-timeout-seconds: 5
  score-threshold: 60

# ── Spring Boot ──
spring:
  main:
    web-application-type: none    # CLI app, no web server
    banner-mode: off              # Clean CLI output
```

### Swapping Adapters (Example: Real HTTP Registry)

To swap from simulated to a real HTTP-based registry, you would:

1. Create `HttpRegistryAdapter implements RegistryPort` in `adapter/outbound/registry/`.
2. Add a `@Profile("production")` or `@ConditionalOnProperty` annotation:

```java
@Configuration
public class AdapterConfig {

    @Bean
    @ConditionalOnProperty(name = "adapter.registry.type", havingValue = "simulated", matchIfMissing = true)
    public RegistryPort simulatedRegistryPort(...) {
        return new SimulatedRegistryAdapter();
    }

    @Bean
    @ConditionalOnProperty(name = "adapter.registry.type", havingValue = "http")
    public RegistryPort httpRegistryPort(
            @Value("${adapter.registry.url}") String baseUrl) {
        return new HttpRegistryAdapter(baseUrl);
    }
}
```

Then in `application.yml`:
```yaml
adapter:
  registry:
    type: http
    url: https://registry.gov/api/v1
```

**Zero changes** to domain, application, or other adapter code.

### Wiring Diagram

```
Spring IoC Container (composition root)
  │
  │  application.yml provides:
  │    cache.file-path, cache.ttl-hours,
  │    adapter.*.seed, pipeline.*
  │
  ├─ AdapterConfig
  │   ├─ @Bean RegistryPort            ← SimulatedRegistryAdapter(seed?)
  │   ├─ @Bean JudicialPort            ← SimulatedJudicialAdapter(seed?)
  │   ├─ @Bean ComplianceBureauPort    ← SimulatedComplianceBureauAdapter(seed?)
  │   ├─ @Bean QualificationScorePort  ← RandomScoreAdapter(seed?)
  │   └─ @Bean ComplianceCachePort     ← FileComplianceCacheAdapter(path, ttl)
  │
  ├─ ServiceConfig
  │   ├─ @Bean QualifyLeadUseCase      ← LeadQualificationService(all ports)
  │   └─ @Bean CliAdapter              ← CliAdapter(useCase)
  │
  └─ Application (CommandLineRunner)
      └─ cliAdapter.run(args)
```

### What stays framework-agnostic?

| Package | Spring annotations? | Reason |
|---------|---------------------|--------|
| `domain.*` | **None** | Pure business objects, zero dependencies |
| `application.*` | **None** | Use cases and port interfaces, plain Java |
| `adapter.*` | **None** | Adapters are POJOs; Spring wires them from outside |
| `config.*` | **Yes** (`@Configuration`, `@Bean`, `@Value`) | This is the only Spring-aware code |
| `Application.java` | **Yes** (`@SpringBootApplication`, `CommandLineRunner`) | Entry point only |

This means you could **remove Spring entirely** by replacing `config/` and `Application.java` with a single `Main.java` that does manual wiring — the rest of the codebase wouldn't change.

---

## DTO Strategy (Anti-Corruption Layer)

### Why DTOs?

Domain entities must be **isolated from external data representations**. Without DTOs, changes in an external API response format (e.g., a JSON field rename) would propagate into the domain layer, violating the dependency rule. DTOs act as an **anti-corruption layer** between the outside world and the domain.

### DTO Rules

| Rule | Description |
|------|-------------|
| **DTOs live in the adapter layer only** | They are never imported by domain or application code |
| **DTOs are plain data carriers** | No business logic, no validation beyond deserialization |
| **DTOs are mutable-friendly** | They can have default constructors, setters, or public fields for framework compatibility (e.g., Gson) |
| **Mappers convert DTO ↔ Domain** | Each adapter package has a dedicated mapper class |
| **Mappers are pure functions** | Stateless, no side effects, easy to unit test |
| **Domain objects are never serialized directly** | Gson/Jackson never touches domain records |

### Inbound DTOs (CLI → Domain)

```
┌──────────────┐    LeadRequestMapper     ┌──────────┐
│ LeadRequest  │ ──────────────────────► │   Lead   │
│ (DTO)        │   validates + converts   │ (Domain) │
│              │   String → LocalDate     │          │
│ nationalId   │   raw input → validated  │          │
│ birthdate    │   object                 │          │
│ firstName    │                          │          │
│ lastName     │                          │          │
│ email        │                          │          │
└──────────────┘                          └──────────┘
```

**`LeadRequest`** (inbound DTO):
```java
public record LeadRequest(
    String nationalId,
    String birthdate,   // String "YYYY-MM-DD", NOT LocalDate
    String firstName,
    String lastName,
    String email
) {}
```
- All fields are raw `String` — no domain types.
- No validation — that's the mapper's job.

**`LeadRequestMapper`**:
```java
public class LeadRequestMapper {
    public Lead toDomain(LeadRequest request) {
        LocalDate birthdate = LocalDate.parse(request.birthdate()); // may throw DateTimeParseException
        return new Lead(
            request.nationalId(),
            birthdate,
            request.firstName(),
            request.lastName(),
            request.email()
        ); // Lead constructor validates invariants
    }
}
```
- Converts `String` birthdate → `LocalDate`.
- Delegates business validation to the `Lead` constructor.
- Throws clear exceptions for format errors (parsing) vs business errors (invalid lead).

### Outbound DTOs (Domain → CLI)

```
┌───────────────┐  QualificationResponseMapper  ┌─────────────────────────┐
│PipelineResult │ ─────────────────────────────►│ QualificationResponse   │
│ (Domain)      │   extracts display-ready data  │ (DTO)                   │
│               │                                │                         │
│               │                                │ nationalId: String      │
│               │                                │ fullName: String        │
│               │                                │ status: String          │
│               │                                │ steps: List<StepResult> │
│               │                                │ score: Integer (nullable)│
│               │                                │ message: String         │
└───────────────┘                                └─────────────────────────┘
```

**`QualificationResponse`** (outbound DTO):
```java
public record QualificationResponse(
    String nationalId,
    String fullName,
    String status,             // "APPROVED", "REJECTED", "MANUAL_REVIEW"
    List<StepResult> steps,
    Integer score,             // nullable — only present when score step executed
    String message             // human-readable summary
) {
    public record StepResult(
        String name,
        boolean passed,
        String detail
    ) {}
}
```
- Flat, presentation-ready structure — the CLI just prints it.
- No domain types leak out.

**`QualificationResponseMapper`**:
```java
public class QualificationResponseMapper {
    public QualificationResponse toResponse(PipelineResult result, Lead lead) {
        // Maps domain ValidationResults → StepResult DTOs
        // Extracts score from validation message if present
        // Builds human-readable summary message
    }
}
```

### Outbound DTOs (External Service Adapters)

Each external service adapter works with its own API DTO and mapper:

```
External System (simulated)
        │
        ▼
┌──────────────────┐     Mapper        ┌─────────────────────┐
│ RegistryApiResponse│ ──────────────► │ RegistryCheckResult  │
│ (DTO)             │   converts to    │ (Domain Value Object)│
│                   │   domain type    │                      │
│ found: boolean    │                  │ status: RegistryStatus│
│ firstName: String │                  │ detail: String       │
│ lastName: String  │                  │                      │
│ birthdate: String │                  │                      │
│ matchScore: double│                  │                      │
└──────────────────┘                   └─────────────────────┘
```

**`RegistryApiResponse`** (external service DTO):
```java
public record RegistryApiResponse(
    boolean found,
    String firstName,    // as returned by the external system
    String lastName,
    String birthdate,    // String format from external API
    double matchScore    // external system's confidence score
) {}
```

**`RegistryMapper`**:
```java
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
        String mismatchDetail = buildMismatchDetail(response, lead); // e.g., "Name mismatch: expected 'John Doe', got 'Juan Perez'"
        return new RegistryCheckResult(RegistryStatus.MISMATCH, mismatchDetail);
    }
}
```

**`JudicialApiResponse`**:
```java
public record JudicialApiResponse(
    boolean hasRecords,
    int recordCount          // external system may return count
) {}
```

**`JudicialMapper`**:
```java
public class JudicialMapper {
    public JudicialCheckResult toDomain(JudicialApiResponse response) {
        JudicialStatus status = response.hasRecords() ? JudicialStatus.HAS_RECORDS : JudicialStatus.CLEAN;
        return new JudicialCheckResult(status);
    }
}
```

**`ComplianceApiResponse`**:
```java
public record ComplianceApiResponse(
    String status,           // "CLEAR", "FLAGGED" — String from external API, not our enum
    String checkedAt,        // ISO-8601 timestamp from external system
    String source            // "OFAC", "EU_SANCTIONS", etc.
) {}
```

**`ComplianceMapper`**:
```java
public class ComplianceMapper {
    public ComplianceCheckResult toDomain(ComplianceApiResponse response) {
        ComplianceStatus status = ComplianceStatus.valueOf(response.status().toUpperCase());
        return new ComplianceCheckResult(status);
    }
}
```

### Cache DTOs

**`CacheEntryDto`** (serialized to JSON file):
```java
public class CacheEntryDto {
    public String status;       // "CLEAR" or "FLAGGED" — String, not enum
    public String timestamp;    // ISO-8601 string
    // Gson-friendly: public fields, no-arg constructor implied
}
```

**`CacheMapper`**:
```java
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
```

### DTO Boundary Summary

```
                    ┌─────────────────────────────┐
   CLI args ───────►│  LeadRequest (DTO)           │
                    │       │                      │
                    │       ▼ LeadRequestMapper     │
                    │  Lead (Domain)                │
                    │       │                      │
                    │       ▼ Service orchestrates  │
                    │       │                      │
                    │  RegistryPort ◄── SimulatedRegistryAdapter ◄── RegistryApiResponse (DTO)
                    │       │              └── RegistryMapper converts to domain
                    │       │                      │
                    │  JudicialPort ◄── SimulatedJudicialAdapter ◄── JudicialApiResponse (DTO)
                    │       │              └── JudicialMapper converts to domain
                    │       │                      │
                    │  ComplianceBureauPort ◄── SimulatedComplianceAdapter ◄── ComplianceApiResponse (DTO)
                    │       │              └── ComplianceMapper converts to domain
                    │       │                      │
                    │  ComplianceCachePort ◄── FileCacheAdapter ◄── CacheEntryDto (DTO)
                    │       │              └── CacheMapper converts to domain
                    │       │                      │
                    │  PipelineResult (Domain)      │
                    │       │                      │
                    │       ▼ QualificationResponseMapper
                    │  QualificationResponse (DTO)  │
                    │       │                      │
                    └───────┼──────────────────────┘
                            ▼
                    CLI output (formatted)
```

**Key insight:** Domain objects **never cross the adapter boundary**. Every piece of data entering or leaving the application passes through a DTO + mapper. This means:
- External API format changes → update DTO + mapper only
- Domain model changes → update mappers, adapters unaffected
- CLI output format changes → update response DTO + mapper only

---

## Data Flow

```
CLI Input (raw strings)
   │
   ▼
CliAdapter.run(args)
   │
   ├─ parses args → creates LeadRequest (DTO)
   ├─ LeadRequestMapper.toDomain(request) → Lead (domain)
   │
   ▼
QualifyLeadUseCase.qualify(Lead)
   │  (implemented by LeadQualificationService)
   │  (all interactions below use domain types only)
   │
   ├─ RegistryPort.check(lead) → RegistryCheckResult     ──┐
   │   (adapter internally: simulate → RegistryApiResponse   ├─ parallel
   │    → RegistryMapper.toDomain → return domain type)      │
   ├─ JudicialPort.check(nationalId) → JudicialCheckResult ─┘
   │   (adapter internally: simulate → JudicialApiResponse
   │    → JudicialMapper.toDomain → return domain type)
   │
   ├─ ComplianceCachePort.get(nationalId)
   │   (adapter internally: read file → CacheEntryDto
   │    → CacheMapper.toDomain → return domain type)
   │   ├─ cache hit → use cached ComplianceCheckResult
   │   └─ cache miss → ComplianceBureauPort.check(nationalId)
   │                    (adapter: simulate → ComplianceApiResponse
   │                     → ComplianceMapper.toDomain → return)
   │                    └─ ComplianceCachePort.put(nationalId, result)
   │                        (adapter: CacheMapper.toDto → write file)
   │
   ├─ QualificationScorePort.generate() → int
   │
   └─ builds PipelineResult (domain)
         │
         ▼
QualificationResponseMapper.toResponse(result, lead) → QualificationResponse (DTO)
         │
         ▼
CliAdapter.printResult(QualificationResponse)
   │
   ▼
Exit(code)
```

---

## Error Handling Strategy

| Scenario | Handling | Result |
|----------|----------|--------|
| Invalid CLI input format | `DateTimeParseException` in mapper | CLI prints "Invalid date format", exits with code 1 |
| Invalid Lead data | `InvalidLeadException` in `Lead` constructor (via mapper) | CLI prints validation error, exits with code 1 |
| Registry/Judicial timeout | `CompletableFuture.orTimeout(5s)` → `TimeoutException` | Caught in service → `ValidationResult.fail()` → REJECTED |
| Compliance Bureau down | `ComplianceBureauUnavailableException` caught in service | → MANUAL_REVIEW (not crash) |
| Compliance cache I/O error | Caught in `FileComplianceCacheAdapter` | Cache miss → proceed to external call |
| DTO mapping error | Caught in adapter, wrapped as domain-meaningful exception | Adapter handles gracefully |
| Unexpected error in pipeline | `QualificationException` wraps root cause | CLI catches, prints error, exits with code 3 |
