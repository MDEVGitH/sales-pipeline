# 05 — Orchestration & Resilience Specification

## Overview

The `LeadQualificationService` is the **heart of the application layer**. It implements the `QualifyLeadUseCase` inbound port and orchestrates the entire validation pipeline by coordinating outbound ports.

It has **zero knowledge** of how external services are implemented — it only knows port interfaces.

---

## Inbound Port

```java
public interface QualifyLeadUseCase {
    PipelineResult qualify(Lead lead);
}
```

**Contract:**
- Accepts a valid `Lead` (validated at construction).
- Returns a `PipelineResult` — **never throws** for expected business scenarios.
- Only throws `QualificationException` for truly unexpected errors (programming bugs, JVM issues).

---

## `LeadQualificationService`

### Constructor (Dependency Injection via Ports)

```java
public LeadQualificationService(
    RegistryPort registryPort,
    JudicialPort judicialPort,
    ComplianceBureauPort complianceBureauPort,
    QualificationScorePort qualificationScorePort,
    ComplianceCachePort complianceCachePort
)
```

All dependencies are **interfaces** (ports). No concrete adapter is referenced.

### `qualify(Lead lead)` — Orchestration Flow

```
METHOD qualify(lead):

  LOG "[PIPELINE] Starting qualification for nationalId={lead.nationalId}"

  // ──── PHASE 1: Parallel Execution ────────────────────────────
  registryFuture  = CompletableFuture.supplyAsync(() → executeRegistryCheck(lead))
  judicialFuture  = CompletableFuture.supplyAsync(() → executeJudicialCheck(lead))

  TRY:
    CompletableFuture.allOf(registryFuture, judicialFuture)
                     .get(10, TimeUnit.SECONDS)  // global timeout for parallel phase
  CATCH TimeoutException:
    RETURN PipelineResult(timeout results, REJECTED)

  registryResult  = registryFuture.get()
  judicialResult  = judicialFuture.get()
  results = [registryResult, judicialResult]

  // ──── SHORT-CIRCUIT CHECK ────────────────────────────────────
  IF !registryResult.success() OR !judicialResult.success():
    LOG "[PIPELINE] Qualification REJECTED — parallel validation failed"
    RETURN PipelineResult(results, REJECTED, null)

  // ──── PHASE 2: Compliance Bureau (Sequential) ────────────────
  complianceResult = executeComplianceCheck(lead.nationalId)

  IF complianceResult is MANUAL_REVIEW:
    LOG "[PIPELINE] Qualification requires MANUAL_REVIEW — compliance bureau unavailable"
    RETURN PipelineResult(results, MANUAL_REVIEW, null)

  results.add(complianceResult)

  IF !complianceResult.success():
    LOG "[PIPELINE] Qualification REJECTED — compliance check failed"
    RETURN PipelineResult(results, REJECTED, null)

  // ──── PHASE 3: Qualification Score (Sequential) ──────────────
  scoreResult = executeScoreCheck()
  results.add(scoreResult)

  IF !scoreResult.success():
    LOG "[PIPELINE] Qualification REJECTED — score below threshold"
    RETURN PipelineResult(results, REJECTED, null)

  // ──── ALL PASSED ─────────────────────────────────────────────
  score = extractScore(scoreResult)
  prospect = Prospect.fromLead(lead, score)
  LOG "[PIPELINE] Qualification APPROVED — lead converted to prospect"
  RETURN PipelineResult(results, APPROVED, prospect)
```

---

## Private Helper Methods

### `executeRegistryCheck(Lead lead) → ValidationResult`
```
result = registryPort.check(lead)
MATCH     → ValidationResult.pass("NationalRegistry", "Person verified in national registry")
MISMATCH  → ValidationResult.fail("NationalRegistry", "Data mismatch: " + result.detail())
NOT_FOUND → ValidationResult.fail("NationalRegistry", "National ID not found in registry")
```

### `executeJudicialCheck(Lead lead) → ValidationResult`
```
result = judicialPort.check(lead.nationalId())
CLEAN       → ValidationResult.pass("JudicialRecords", "No judicial records found")
HAS_RECORDS → ValidationResult.fail("JudicialRecords", "Judicial records found for this person")
```

### `executeComplianceCheck(String nationalId) → ValidationResult | MANUAL_REVIEW signal`
```
// 1. Check cache first
cached = complianceCachePort.get(nationalId)
IF cached.isPresent():
  LOG "[COMPLIANCE] Cache HIT for nationalId={nationalId}"
  complianceResult = cached.get()
ELSE:
  LOG "[COMPLIANCE] Cache MISS — calling external service"
  TRY:
    complianceResult = complianceBureauPort.check(nationalId)
    complianceCachePort.put(nationalId, complianceResult)
  CATCH ComplianceBureauUnavailableException:
    LOG "[COMPLIANCE] Service UNAVAILABLE"
    RETURN MANUAL_REVIEW_SIGNAL  // special return to trigger MANUAL_REVIEW path

// 2. Map to ValidationResult
CLEAR   → ValidationResult.pass("ComplianceBureau", "Not found in sanctions list")
FLAGGED → ValidationResult.fail("ComplianceBureau", "FLAGGED in sanctions/OFAC list")
```

**Implementation note for MANUAL_REVIEW signal:**
Use a nullable `ValidationResult` return — if `null`, the service treats it as MANUAL_REVIEW. Alternatively, use a dedicated sealed interface or a `ComplianceOutcome` type with three variants (CLEAR, FLAGGED, UNAVAILABLE). The sealed interface approach is preferred for type safety:

```java
public sealed interface ComplianceOutcome {
    record Clear() implements ComplianceOutcome {}
    record Flagged() implements ComplianceOutcome {}
    record Unavailable(String reason) implements ComplianceOutcome {}
}
```

### `executeScoreCheck() → ValidationResult`
```
score = qualificationScorePort.generateScore()
IF score > 60:
  RETURN ValidationResult.pass("QualificationScore", "Score: " + score + "/100 (threshold: >60)")
ELSE:
  RETURN ValidationResult.fail("QualificationScore", "Score: " + score + "/100 (threshold: >60, not met)")
```

---

## Parallelism Specification

### Why `CompletableFuture`

| Alternative | Rejected because |
|-------------|-----------------|
| Sequential execution | Steps 1a and 1b are independent — sequential wastes ~500ms average |
| `ExecutorService` + `Future` | More verbose, `CompletableFuture` provides cleaner API |
| Virtual Threads (Java 21) | Project targets Java 17 |
| Reactive (Reactor/RxJava) | Over-engineering for 2 parallel tasks in a CLI |

### Thread Pool

Use the **common ForkJoinPool** (default for `CompletableFuture.supplyAsync()` without explicit executor). This is appropriate for a CLI application with 2 parallel tasks.

### Timeout Strategy

| Scope | Timeout | Mechanism |
|-------|---------|-----------|
| Individual parallel step | 5 seconds | `CompletableFuture.orTimeout(5, TimeUnit.SECONDS)` |
| Global parallel phase | 10 seconds | `.allOf().get(10, TimeUnit.SECONDS)` |
| Compliance Bureau call | 5 seconds | Handled inside `executeComplianceCheck` |
| Score generation | No timeout | Internal, instant |

---

## Resilience Patterns

### Pattern 1: Graceful Degradation (Compliance Bureau)

```
                    ┌─────────────────┐
                    │ Compliance Port  │
                    └────────┬────────┘
                             │
                    ┌────────┴────────┐
                    │                 │
               SUCCESS           EXCEPTION
                    │                 │
              ┌─────┴─────┐    ┌─────┴──────┐
              │           │    │            │
            CLEAR     FLAGGED  MANUAL_REVIEW│
              │           │    │            │
           continue    REJECTED  return to   │
           pipeline       │    human review  │
                          │                  │
```

**Rationale:** A compliance bureau outage should not block the entire CRM pipeline. Leads are valuable — routing to manual review preserves business flow while maintaining compliance.

### Pattern 2: Cache-Aside (Compliance Responses)

```
         ┌──────────┐
    ┌───►│  Cache    │◄──── put(id, result)
    │    └──────────┘          ▲
    │         │                │
  get(id)     │           on success
    │    ┌────┴─────┐         │
    │    │          │    ┌────┴──────┐
    │  HIT       MISS───►│ External   │
    │    │              │ Service    │
    │    ▼              └───────────┘
    │ return cached
    │ result
```

**Cache is an optimization, not a correctness requirement:**
- Cache read failure → proceed as cache miss
- Cache write failure → log warning, continue
- The pipeline never fails because of cache issues

### Pattern 3: Short-Circuit Evaluation

Each phase checks the result of the previous phase before proceeding:
- This minimizes unnecessary external calls
- Failed leads don't waste compliance bureau quota
- Low-score leads never reach external services at all... wait, score is last. Correct: if earlier steps fail, later steps don't execute.

---

## Logging Contract

All pipeline activity is logged to stdout with these rules:

| Level | Prefix | When |
|-------|--------|------|
| Progress | `[PIPELINE]` | Pipeline start, phase transitions, final result |
| Detail | `[REGISTRY]` | Registry validation activity (delegated to adapter) |
| Detail | `[JUDICIAL]` | Judicial check activity (delegated to adapter) |
| Detail | `[COMPLIANCE]` | Compliance activity including cache hits/misses |
| Detail | `[SCORE]` | Score generation and threshold comparison |

**Format example for a full successful run:**
```
[PIPELINE] Starting qualification for nationalId=123456789
[REGISTRY] Checking nationalId=123456789... MATCH (523ms)
[JUDICIAL] Checking nationalId=123456789... CLEAN (847ms)
[COMPLIANCE] Cache MISS — calling external service
[COMPLIANCE] Service call for nationalId=123456789... CLEAR (234ms)
[SCORE] Generated score: 78/100 (threshold: >60)
[PIPELINE] Qualification APPROVED — lead converted to prospect
```
