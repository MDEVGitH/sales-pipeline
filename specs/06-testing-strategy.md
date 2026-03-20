# 06 — Testing Strategy Specification

## Overview

The testing strategy follows the **Test Pyramid**: many fast unit tests at the base, fewer integration tests at the middle, and minimal end-to-end tests at the top. The hexagonal architecture makes this natural — domain and application layers are tested in pure isolation, while adapters are tested against their real infrastructure (files, threads).

---

## Test Pyramid

```
        ┌─────────┐
        │  E2E    │   1-2 tests  (full CLI run)
        │  Tests  │
       ─┼─────────┼─
       │           │
       │Integration│   8-12 tests (orchestrator + real adapters)
       │  Tests    │
      ─┼───────────┼─
      │             │
      │  Unit Tests │   40+ tests (domain, ports, adapters in isolation)
      │             │
      └─────────────┘
```

---

## Testing Principles

| Principle | Application |
|-----------|-------------|
| **Test behavior, not implementation** | Assert on outputs and side effects, not internal method calls |
| **One assertion per concept** | Each test verifies one logical behavior |
| **Arrange-Act-Assert** | Clear structure in every test method |
| **Descriptive test names** | `shouldRejectLead_whenRegistryReturns_MISMATCH` |
| **No test interdependence** | Each test runs independently, creates its own state |
| **Fast feedback** | Unit tests run in < 1 second total, no Thread.sleep in unit tests |
| **Deterministic** | Tests never depend on randomness — use seeded or stubbed services |

---

## Unit Tests

### Layer: Domain Model

**File:** `domain/model/LeadTest.java`

| Test | Input | Expected |
|------|-------|----------|
| `shouldCreateValidLead` | All valid fields | Lead instance created |
| `shouldReject_whenNationalIdIsNull` | nationalId=null | `NullPointerException` |
| `shouldReject_whenNationalIdIsBlank` | nationalId="" | `InvalidLeadException` |
| `shouldReject_whenBirthdateIsNull` | birthdate=null | `NullPointerException` |
| `shouldReject_whenBirthdateIsInFuture` | birthdate=tomorrow | `InvalidLeadException` |
| `shouldReject_whenBirthdateIsToday` | birthdate=today | `InvalidLeadException` |
| `shouldReject_whenAgeIsUnder18` | birthdate=10 years ago | `InvalidLeadException` |
| `shouldReject_whenFirstNameIsNull` | firstName=null | `NullPointerException` |
| `shouldReject_whenFirstNameIsBlank` | firstName=" " | `InvalidLeadException` |
| `shouldReject_whenLastNameIsNull` | lastName=null | `NullPointerException` |
| `shouldReject_whenLastNameIsBlank` | lastName="" | `InvalidLeadException` |
| `shouldReject_whenEmailIsNull` | email=null | `NullPointerException` |
| `shouldReject_whenEmailIsBlank` | email="" | `InvalidLeadException` |
| `shouldReject_whenEmailHasNoAtSign` | email="invalid" | `InvalidLeadException` |
| `shouldReject_whenEmailHasNoDomain` | email="user@" | `InvalidLeadException` |
| `shouldAcceptValidEmail` | email="user@domain.com" | Lead created |

**File:** `domain/model/ProspectTest.java`

| Test | Input | Expected |
|------|-------|----------|
| `shouldCreateProspectFromLead` | valid Lead, score=75 | Prospect with all Lead fields + score + timestamp |
| `shouldReject_whenScoreBelow61` | score=60 | `IllegalArgumentException` |
| `shouldReject_whenScoreAbove100` | score=101 | `IllegalArgumentException` |
| `shouldAcceptBoundaryScore61` | score=61 | Prospect created |
| `shouldAcceptBoundaryScore100` | score=100 | Prospect created |
| `shouldSetQualifiedAtToNow` | valid inputs | qualifiedAt ≈ now (±1 second tolerance) |

**File:** `domain/model/ValidationResultTest.java`

| Test | Input | Expected |
|------|-------|----------|
| `shouldCreatePassResult` | pass("Registry", "OK") | success=true, validatorName="Registry", message="OK" |
| `shouldCreateFailResult` | fail("Registry", "Not found") | success=false |
| `shouldSetTimestampAutomatically` | any factory method | timestamp ≈ now |
| `shouldReject_whenValidatorNameIsNull` | validatorName=null | `NullPointerException` |
| `shouldReject_whenMessageIsNull` | message=null | `NullPointerException` |

**File:** `domain/model/PipelineResultTest.java`

| Test | Input | Expected |
|------|-------|----------|
| `shouldCreateApprovedResultWithProspect` | APPROVED + prospect | getProspect() returns present |
| `shouldCreateRejectedResultWithoutProspect` | REJECTED + null | getProspect() returns empty |
| `shouldCreateManualReviewResult` | MANUAL_REVIEW + null | status is MANUAL_REVIEW |
| `shouldReject_whenApprovedButNoProspect` | APPROVED + null | `IllegalArgumentException` |
| `shouldReject_whenRejectedButHasProspect` | REJECTED + prospect | `IllegalArgumentException` |
| `shouldHaveUnmodifiableResultsList` | any valid result | modifying returned list throws `UnsupportedOperationException` |

---

### Layer: Application (Service with Stubbed Ports)

The service tests are the **most important unit tests** — they verify the orchestration logic in isolation from any infrastructure.

**Test doubles:** Create simple **stub implementations** of each port interface directly in the test class (or as inner classes). Do NOT use mocking frameworks — keep it simple and explicit.

**File:** `application/service/LeadQualificationServiceTest.java`

#### Happy Path

| Test | Stubs | Expected |
|------|-------|----------|
| `shouldApprove_whenAllValidationsPass` | Registry→MATCH, Judicial→CLEAN, Compliance→CLEAR, Score→75 | APPROVED, prospect present, 4 results |
| `shouldApprove_withBoundaryScore61` | all pass, Score→61 | APPROVED |

#### Registry Failures

| Test | Stubs | Expected |
|------|-------|----------|
| `shouldReject_whenRegistryReturnsMismatch` | Registry→MISMATCH, Judicial→CLEAN | REJECTED, 2 results, no compliance call |
| `shouldReject_whenRegistryReturnsNotFound` | Registry→NOT_FOUND, Judicial→CLEAN | REJECTED, 2 results |

#### Judicial Failures

| Test | Stubs | Expected |
|------|-------|----------|
| `shouldReject_whenJudicialReturnsHasRecords` | Registry→MATCH, Judicial→HAS_RECORDS | REJECTED, 2 results |

#### Both Parallel Steps Fail

| Test | Stubs | Expected |
|------|-------|----------|
| `shouldReject_whenBothParallelStepsFail` | Registry→NOT_FOUND, Judicial→HAS_RECORDS | REJECTED, 2 results, both failures reported |

#### Compliance Failures

| Test | Stubs | Expected |
|------|-------|----------|
| `shouldReject_whenComplianceReturnsFlagged` | Registry→MATCH, Judicial→CLEAN, Compliance→FLAGGED | REJECTED, 3 results |
| `shouldReturnManualReview_whenComplianceServiceUnavailable` | Registry→MATCH, Judicial→CLEAN, Compliance→throws | MANUAL_REVIEW, 2 results |

#### Score Failures

| Test | Stubs | Expected |
|------|-------|----------|
| `shouldReject_whenScoreEquals60` | all pass, Score→60 | REJECTED, 4 results |
| `shouldReject_whenScoreEquals0` | all pass, Score→0 | REJECTED, 4 results |

#### Short-Circuit Verification

| Test | Stubs | Expected |
|------|-------|----------|
| `shouldNotCallCompliance_whenParallelStepsFail` | Registry→NOT_FOUND, tracking compliance stub | compliance stub never called |
| `shouldNotCallScore_whenComplianceFails` | compliance→FLAGGED, tracking score stub | score stub never called |
| `shouldNotCallScore_whenComplianceUnavailable` | compliance→throws, tracking score stub | score stub never called |

#### Cache Interaction

| Test | Stubs | Expected |
|------|-------|----------|
| `shouldUseCache_whenCacheHit` | cache.get→CLEAR, tracking bureau stub | bureau stub never called, result is APPROVED path |
| `shouldCallBureauAndCache_whenCacheMiss` | cache.get→empty, bureau→CLEAR, tracking cache stub | bureau called, cache.put called with CLEAR |
| `shouldNotCacheError_whenBureauUnavailable` | bureau→throws, tracking cache stub | cache.put never called |

#### Parallel Execution Verification

| Test | Stubs | Expected |
|------|-------|----------|
| `shouldExecuteRegistryAndJudicialInParallel` | both with 500ms delay | total time < 800ms (not 1000ms sequential) |

---

### Layer: Infrastructure — Mappers (Pure Functions, No I/O)

Mapper tests are fast and deterministic — they verify the DTO ↔ Domain conversion logic in isolation.

**File:** `adapter/outbound/registry/RegistryMapperTest.java`

| Test | Input DTO | Expected Domain |
|------|-----------|-----------------|
| `shouldMapToMatch_whenFoundAndDataMatches` | `RegistryApiResponse(true, "John", "Doe", "1990-01-15", 0.95)` with matching Lead | `RegistryCheckResult(MATCH, ...)` |
| `shouldMapToMismatch_whenFoundButNameDiffers` | `RegistryApiResponse(true, "Juan", "Perez", "1990-01-15", 0.3)` | `RegistryCheckResult(MISMATCH, "Name mismatch...")` |
| `shouldMapToMismatch_whenFoundButBirthdateDiffers` | `RegistryApiResponse(true, "John", "Doe", "1985-06-20", 0.4)` | `RegistryCheckResult(MISMATCH, "Birthdate mismatch...")` |
| `shouldMapToNotFound_whenNotFound` | `RegistryApiResponse(false, null, null, null, 0.0)` | `RegistryCheckResult(NOT_FOUND, ...)` |
| `shouldMatchCaseInsensitive` | `RegistryApiResponse(true, "JOHN", "DOE", ...)` with "John", "Doe" Lead | `RegistryCheckResult(MATCH, ...)` |

**File:** `adapter/outbound/judicial/JudicialMapperTest.java`

| Test | Input DTO | Expected Domain |
|------|-----------|-----------------|
| `shouldMapToClean_whenNoRecords` | `JudicialApiResponse(false, 0)` | `JudicialCheckResult(CLEAN)` |
| `shouldMapToHasRecords_whenRecordsExist` | `JudicialApiResponse(true, 3)` | `JudicialCheckResult(HAS_RECORDS)` |

**File:** `adapter/outbound/compliance/ComplianceMapperTest.java`

| Test | Input DTO | Expected Domain |
|------|-----------|-----------------|
| `shouldMapToClear` | `ComplianceApiResponse("CLEAR", "2026-03-20T10:00:00", "OFAC")` | `ComplianceCheckResult(CLEAR)` |
| `shouldMapToFlagged` | `ComplianceApiResponse("FLAGGED", "2026-03-20T10:00:00", "OFAC")` | `ComplianceCheckResult(FLAGGED)` |
| `shouldHandleLowercaseStatus` | `ComplianceApiResponse("clear", ...)` | `ComplianceCheckResult(CLEAR)` |
| `shouldThrow_whenUnknownStatus` | `ComplianceApiResponse("UNKNOWN", ...)` | `IllegalArgumentException` |

**File:** `adapter/outbound/cache/CacheMapperTest.java`

| Test | Scenario | Expected |
|------|----------|----------|
| `shouldConvertDomainToDto` | `ComplianceCheckResult(CLEAR)` | `CacheEntryDto{status="CLEAR", timestamp=~now}` |
| `shouldConvertDtoToDomain` | `CacheEntryDto{status="FLAGGED", timestamp=...}` | `ComplianceCheckResult(FLAGGED)` |
| `shouldDetectExpiredEntry` | dto with timestamp 25 hours ago, ttl=24 | `isExpired() == true` |
| `shouldDetectValidEntry` | dto with timestamp 1 hour ago, ttl=24 | `isExpired() == false` |
| `shouldDetectBoundaryExpiration` | dto with timestamp exactly 24 hours ago, ttl=24 | `isExpired() == true` |

**File:** `adapter/inbound/cli/LeadRequestMapperTest.java`

| Test | Input DTO | Expected |
|------|-----------|----------|
| `shouldMapValidRequest` | `LeadRequest("123", "1990-01-15", "John", "Doe", "j@d.com")` | valid `Lead` |
| `shouldThrow_whenBirthdateFormatInvalid` | birthdate="not-a-date" | `DateTimeParseException` |
| `shouldThrow_whenLeadValidationFails` | blank nationalId | `InvalidLeadException` (from Lead constructor) |

**File:** `adapter/inbound/cli/QualificationResponseMapperTest.java`

| Test | Input Domain | Expected DTO |
|------|-------------|--------------|
| `shouldMapApprovedResult` | PipelineResult(APPROVED, 4 results, prospect) | `QualificationResponse(status="APPROVED", 4 steps, score=75)` |
| `shouldMapRejectedResult` | PipelineResult(REJECTED, 2 results, null) | `QualificationResponse(status="REJECTED", 2 steps, score=null)` |
| `shouldMapManualReviewResult` | PipelineResult(MANUAL_REVIEW, 2 results, null) | `QualificationResponse(status="MANUAL_REVIEW", 2 steps)` |
| `shouldMapStepDetails` | result with "NationalRegistry" pass | `StepResult(name="NationalRegistry", passed=true, ...)` |

---

### Layer: Infrastructure — Adapters (with I/O)

**File:** `adapter/outbound/registry/SimulatedRegistryAdapterTest.java`

| Test | Setup | Expected |
|------|-------|----------|
| `shouldReturnValidRegistryStatus` | default adapter | result status is one of MATCH, MISMATCH, NOT_FOUND |
| `shouldSimulateLatency` | default adapter, measure time | elapsed >= 200ms |
| `shouldProduceMatchWithSeededRandom` | seed that produces MATCH | status == MATCH |
| `shouldProduceDeterministicResults_withSeed` | fixed seed, run twice | same sequence of results |
| `shouldReturnDomainType_notDto` | any call | return type is `RegistryCheckResult` (domain), not `RegistryApiResponse` (DTO) |

**File:** `adapter/outbound/judicial/SimulatedJudicialAdapterTest.java`

| Test | Setup | Expected |
|------|-------|----------|
| `shouldReturnValidJudicialStatus` | default adapter | result status is CLEAN or HAS_RECORDS |
| `shouldSimulateLatency` | default adapter, measure time | elapsed >= 300ms |
| `shouldReturnDomainType_notDto` | any call | return type is `JudicialCheckResult` (domain) |

**File:** `adapter/outbound/compliance/SimulatedComplianceBureauAdapterTest.java`

| Test | Setup | Expected |
|------|-------|----------|
| `shouldReturnClearOrFlagged_whenServiceUp` | repeat 100x, catch exceptions | non-exception results are CLEAR or FLAGGED |
| `shouldThrowCheckedException_sometimes` | repeat 100x | at least 1 `ComplianceBureauUnavailableException` (checked, not runtime) |
| `shouldSimulateLatency` | default adapter, measure time | elapsed >= 100ms |

**File:** `adapter/outbound/score/RandomScoreAdapterTest.java`

| Test | Setup | Expected |
|------|-------|----------|
| `shouldGenerateScoreInRange` | repeat 1000x | all scores 0-100 |
| `shouldProduceDeterministicScore_withSeed` | fixed seed | known score value |

**File:** `adapter/outbound/cache/FileComplianceCacheAdapterTest.java`

Uses `@TempDir` (JUnit 5) for isolated file system testing.

| Test | Setup | Expected |
|------|-------|----------|
| `shouldReturnEmpty_whenCacheFileDoesNotExist` | temp dir, no file | `Optional.empty()` |
| `shouldReturnEmpty_whenKeyNotInCache` | cache with other keys | `Optional.empty()` |
| `shouldStoreAndRetrieve_clearResult` | put CLEAR, then get | `Optional.of(CLEAR)` |
| `shouldStoreAndRetrieve_flaggedResult` | put FLAGGED, then get | `Optional.of(FLAGGED)` |
| `shouldReturnEmpty_whenEntryExpired` | put with TTL=1ms, sleep 10ms, get | `Optional.empty()` |
| `shouldOverwriteExistingEntry` | put CLEAR, then put FLAGGED, get | FLAGGED |
| `shouldCreateDirectoriesIfNotExist` | nested path in temp dir | directories created, file written |
| `shouldHandleCorruptedCacheFile` | write invalid JSON to file, get | `Optional.empty()` (no crash) |
| `shouldPersistAcrossInstances` | put with instance A, get with instance B (same file) | value found |
| `shouldStoreMultipleEntries` | put 3 different IDs, get each | all found |

---

## Integration Tests

Integration tests verify that components work together correctly. They use **real adapters** with deterministic seeds for reproducibility.

### Approach: Plain Java (no Spring context)

Integration tests wire components **manually** — no `@SpringBootTest`. This keeps them fast (~seconds, not ~10s for Spring context startup) and tests the actual business flow, not Spring wiring.

Spring wiring correctness is verified separately in configuration tests (see below).

**File:** `integration/LeadQualificationIntegrationTest.java`

### Setup
```java
@Tag("integration")
class LeadQualificationIntegrationTest {

    @TempDir
    Path tempDir;

    private LeadQualificationService service;

    @BeforeEach
    void setUp() {
        // Manual wiring with seeded adapters — same as Spring would do, but explicit
        var registryPort = new SimulatedRegistryAdapter(seed1);
        var judicialPort = new SimulatedJudicialAdapter(seed2);
        var compliancePort = new SimulatedComplianceBureauAdapter(seed3);
        var scorePort = new RandomScoreAdapter(seed4);
        var cachePort = new FileComplianceCacheAdapter(
            tempDir.resolve("cache.json").toString(), 24);

        service = new LeadQualificationService(
            registryPort, judicialPort, compliancePort, scorePort, cachePort);
    }
}
```

| Test | Description | Verification |
|------|-------------|--------------|
| `shouldQualifyLeadEndToEnd_approved` | Use seeds that produce all-pass scenario | PipelineResult.status == APPROVED, prospect non-null |
| `shouldQualifyLeadEndToEnd_rejected` | Use seeds that produce registry NOT_FOUND | PipelineResult.status == REJECTED |
| `shouldQualifyLeadEndToEnd_manualReview` | Use seeds that produce compliance exception | PipelineResult.status == MANUAL_REVIEW |
| `shouldUseCacheOnSecondCall` | Call qualify twice with same nationalId | Second call is faster (cache hit) |
| `shouldRunParallelStepsInParallel` | Measure total execution time | Total time < sum of individual latencies |
| `shouldCollectAllValidationResults` | All-pass scenario | result.validationResults().size() == 4 |
| `shouldShortCircuitAfterParallelFailure` | Registry fails | result.validationResults().size() == 2 |
| `shouldHandleConcurrentQualifications` | Submit 5 leads in parallel via ExecutorService | All complete without errors, no cache corruption |

**File:** `integration/ComplianceCacheIntegrationTest.java`

| Test | Description | Verification |
|------|-------------|--------------|
| `shouldCacheComplianceResultOnFirstCall` | Qualify a lead, check cache file exists | cache file contains the nationalId entry |
| `shouldServeCachedResultOnSecondCall` | Qualify same lead twice, second with bureau that always throws | Second call still CLEAR (from cache), no exception |
| `shouldExpireCacheEntries` | Use 1-second TTL, qualify, wait, qualify again | Second call hits external service (cache expired) |

---

## Spring Configuration Tests

These tests verify that Spring Boot wires everything correctly. They load the full application context but don't test business logic.

**File:** `config/SpringContextTest.java`

```java
@SpringBootTest
@Tag("integration")
class SpringContextTest {

    @Autowired private QualifyLeadUseCase qualifyLeadUseCase;
    @Autowired private RegistryPort registryPort;
    @Autowired private JudicialPort judicialPort;
    @Autowired private ComplianceBureauPort complianceBureauPort;
    @Autowired private QualificationScorePort qualificationScorePort;
    @Autowired private ComplianceCachePort complianceCachePort;
    @Autowired private CliAdapter cliAdapter;
}
```

| Test | Verification |
|------|--------------|
| `contextLoads` | Application context starts without errors |
| `allPortsAreWired` | All `@Autowired` port fields are non-null |
| `qualifyLeadUseCaseIsWired` | `qualifyLeadUseCase` is instance of `LeadQualificationService` |
| `cliAdapterIsWired` | `cliAdapter` is non-null and functional |

**File:** `config/ConfigurationPropertiesTest.java`

| Test | Verification |
|------|--------------|
| `shouldLoadDefaultCachePath` | cache.file-path resolves to `./data/compliance-cache.json` |
| `shouldLoadDefaultTtl` | cache.ttl-hours == 24 |
| `shouldLoadPipelineDefaults` | score-threshold == 60, parallel-timeout == 10 |
| `shouldOverrideWithTestProperties` | `@TestPropertySource` overrides work |

**Test application.yml** (`src/test/resources/application.yml`):
```yaml
cache:
  file-path: ${java.io.tmpdir}/test-compliance-cache.json
  ttl-hours: 1

adapter:
  registry:
    seed: 12345
  judicial:
    seed: 67890
  compliance:
    seed: 11111
  score:
    seed: 99999

pipeline:
  parallel-timeout-seconds: 5
  per-service-timeout-seconds: 3
  score-threshold: 60

spring:
  main:
    web-application-type: none
    banner-mode: off
```

---

## End-to-End Tests (CLI via Spring Boot)

**File:** `e2e/CliEndToEndTest.java`

These tests use `@SpringBootTest` with captured stdout to verify the full CLI flow including Spring wiring.

```java
@SpringBootTest(args = {
    "--nationalId=123456789",
    "--firstName=John",
    "--lastName=Doe",
    "--birthdate=1990-01-15",
    "--email=john@example.com"
})
@Tag("e2e")
class CliEndToEndTest { ... }
```

| Test | Args | Expected stdout contains | Expected exit code |
|------|------|--------------------------|-------------------|
| `shouldPrintApprovedResult` | valid lead, seeded for all-pass via test application.yml | "APPROVED" | 0 |
| `shouldPrintRejectedResult` | valid lead, seeded for registry fail | "REJECTED" | 1 |
| `shouldRejectInvalidEmail` | --email=invalid | error message about email | 1 |

---

## Test Organization

```
src/test/java/com/crm/qualifier/
│
├── domain/
│   └── model/
│       ├── LeadTest.java
│       ├── ProspectTest.java
│       ├── ValidationResultTest.java
│       └── PipelineResultTest.java
│
├── application/
│   └── service/
│       └── LeadQualificationServiceTest.java     ← Most important test class
│
├── adapter/
│   ├── inbound/
│   │   └── cli/
│   │       ├── LeadRequestMapperTest.java        ← Inbound DTO mapping
│   │       └── QualificationResponseMapperTest.java ← Outbound DTO mapping
│   │
│   └── outbound/
│       ├── registry/
│       │   ├── RegistryMapperTest.java           ← DTO → Domain mapping
│       │   └── SimulatedRegistryAdapterTest.java
│       ├── judicial/
│       │   ├── JudicialMapperTest.java           ← DTO → Domain mapping
│       │   └── SimulatedJudicialAdapterTest.java
│       ├── compliance/
│       │   ├── ComplianceMapperTest.java          ← DTO → Domain mapping
│       │   └── SimulatedComplianceBureauAdapterTest.java
│       ├── score/
│       │   └── RandomScoreAdapterTest.java
│       └── cache/
│           ├── CacheMapperTest.java               ← DTO ↔ Domain + TTL logic
│           └── FileComplianceCacheAdapterTest.java
│
├── config/
│   ├── SpringContextTest.java                ← Verifies Spring wiring
│   └── ConfigurationPropertiesTest.java      ← Verifies application.yml loading
│
├── integration/
│   ├── LeadQualificationIntegrationTest.java ← Plain Java, no Spring context
│   └── ComplianceCacheIntegrationTest.java
│
└── e2e/
    └── CliEndToEndTest.java                  ← @SpringBootTest, full CLI flow
```

---

## Test Execution

### Unit Tests Only (fast, < 2 seconds)
```bash
mvn test -Dgroups="unit"
```

### Integration Tests Only
```bash
mvn test -Dgroups="integration"
```

### All Tests
```bash
mvn test
```

### Tag Annotations
```java
// Unit tests
@Tag("unit")
class LeadTest { ... }

// Integration tests
@Tag("integration")
class LeadQualificationIntegrationTest { ... }

// E2E tests
@Tag("e2e")
class CliEndToEndTest { ... }
```

---

## Test Double Strategy

| Port | Unit Test Double | Integration Test |
|------|-----------------|------------------|
| `RegistryPort` | Inline stub returning fixed status | `SimulatedRegistryAdapter` with seed |
| `JudicialPort` | Inline stub returning fixed status | `SimulatedJudicialAdapter` with seed |
| `ComplianceBureauPort` | Inline stub (return or throw) | `SimulatedComplianceBureauAdapter` with seed |
| `QualificationScorePort` | Inline stub returning fixed score | `RandomScoreAdapter` with seed |
| `ComplianceCachePort` | Inline stub (HashMap-based) | `FileComplianceCacheAdapter` with @TempDir |

**No mocking frameworks.** All test doubles are simple inline implementations (lambdas or anonymous classes) of the port interfaces. This keeps tests readable, debuggable, and framework-free.

Example stub:
```java
RegistryPort alwaysMatch = lead -> new RegistryCheckResult(RegistryStatus.MATCH, "Matched");
JudicialPort alwaysClean = nationalId -> new JudicialCheckResult(JudicialStatus.CLEAN);
QualificationScorePort fixedScore = () -> 75;
ComplianceCachePort noCache = new ComplianceCachePort() {
    public Optional<ComplianceCheckResult> get(String id) { return Optional.empty(); }
    public void put(String id, ComplianceCheckResult r) { /* no-op */ }
};
```

This approach is clean, explicit, and leverages the hexagonal architecture — the port interfaces were designed to make testing trivial.
