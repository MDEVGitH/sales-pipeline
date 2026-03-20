# 03 — Validation Pipeline Specification

## Overview

The qualification pipeline is a **multi-step validation workflow** that transforms a `Lead` into a `Prospect`. Each step is defined by a contract (port interface) and executed by the orchestration service (`LeadQualificationService`).

---

## Pipeline Topology

```
         ┌─────────────────────┐
         │       Lead          │
         └─────────┬───────────┘
                   │
          ┌────────┴────────┐
          │                 │
          ▼                 ▼
  ┌───────────────┐ ┌───────────────┐
  │  Step 1a:     │ │  Step 1b:     │    PARALLEL
  │  Registry     │ │  Judicial     │    (CompletableFuture)
  │  Validation   │ │  Records      │
  └───────┬───────┘ └───────┬───────┘
          │                 │
          │   Both must     │
          │    pass         │
          └────────┬────────┘
                   │
                   ▼
          ┌───────────────┐
          │  Step 2:      │
          │  Compliance   │    SEQUENTIAL (depends on 1a + 1b)
          │  Bureau       │    CACHED + RESILIENT
          └───────┬───────┘
                  │
                  ▼
          ┌───────────────┐
          │  Step 3:      │
          │  Qualification│    SEQUENTIAL (depends on 2)
          │  Score        │
          └───────┬───────┘
                  │
                  ▼
         ┌─────────────────────┐
         │  PipelineResult     │
         │  (APPROVED /        │
         │   REJECTED /        │
         │   MANUAL_REVIEW)    │
         └─────────────────────┘
```

---

## Step 1a: National Registry Validation

### Port Interface

```java
public interface RegistryPort {
    RegistryCheckResult check(Lead lead);
}
```

### Contract

| Aspect | Detail |
|--------|--------|
| **Input** | Full `Lead` object |
| **Process** | Send `nationalId`, `firstName`, `lastName`, `birthdate` to external registry. Compare returned data against Lead data. |
| **Output** | `RegistryCheckResult` (see below) |
| **Timeout** | 5 seconds max |
| **On timeout** | Return `MATCH_FAILED` with message "Registry service timed out" |

### `RegistryCheckResult` (Domain Value Object)

```java
public record RegistryCheckResult(RegistryStatus status, String detail) {}

public enum RegistryStatus {
    MATCH,          // Person found AND data matches
    MISMATCH,       // Person found BUT data does NOT match (name or birthdate)
    NOT_FOUND       // Person not found in registry
}
```

### Success Criteria
- `status == MATCH` → validation passes
- `status == MISMATCH` → validation fails with detail explaining which fields differ
- `status == NOT_FOUND` → validation fails with "National ID not found in registry"

### Mapping to `ValidationResult`
```
MATCH     → ValidationResult.pass("NationalRegistry", "Person verified in national registry")
MISMATCH  → ValidationResult.fail("NationalRegistry", "Data mismatch: {detail}")
NOT_FOUND → ValidationResult.fail("NationalRegistry", "National ID not found in registry")
```

---

## Step 1b: Judicial Records Check

### Port Interface

```java
public interface JudicialPort {
    JudicialCheckResult check(String nationalId);
}
```

### Contract

| Aspect | Detail |
|--------|--------|
| **Input** | `nationalId` (String) |
| **Process** | Query external judicial archives for any records linked to this national ID |
| **Output** | `JudicialCheckResult` (see below) |
| **Timeout** | 5 seconds max |
| **On timeout** | Return `HAS_RECORDS` with message "Judicial service timed out — treated as fail" |

### `JudicialCheckResult` (Domain Value Object)

```java
public record JudicialCheckResult(JudicialStatus status) {}

public enum JudicialStatus {
    CLEAN,        // No judicial records found
    HAS_RECORDS   // One or more judicial records found
}
```

### Success Criteria
- `CLEAN` → validation passes
- `HAS_RECORDS` → validation fails

### Mapping to `ValidationResult`
```
CLEAN       → ValidationResult.pass("JudicialRecords", "No judicial records found")
HAS_RECORDS → ValidationResult.fail("JudicialRecords", "Judicial records found for this person")
```

---

## Step 2: Compliance Bureau (OFAC/Sanctions)

### Port Interfaces

```java
public interface ComplianceBureauPort {
    ComplianceCheckResult check(String nationalId) throws ComplianceBureauUnavailableException;
}

public interface ComplianceCachePort {
    Optional<ComplianceCheckResult> get(String nationalId);
    void put(String nationalId, ComplianceCheckResult result);
}
```

### Contract

| Aspect | Detail |
|--------|--------|
| **Precondition** | Steps 1a AND 1b must have both returned `success == true` |
| **Input** | `nationalId` (String) |
| **Process** | 1. Check cache → if hit and not expired, use cached result. 2. If cache miss → call external compliance bureau. 3. Cache the response if successful. |
| **Output** | `ComplianceCheckResult` (see below) |
| **On service failure** | Return `MANUAL_REVIEW` — do NOT fail the entire pipeline |
| **Cache TTL** | 24 hours |

### `ComplianceCheckResult` (Domain Value Object)

```java
public record ComplianceCheckResult(ComplianceStatus status) {}

public enum ComplianceStatus {
    CLEAR,    // Not found in sanctions list
    FLAGGED   // Found in sanctions list
}
```

### `ComplianceBureauUnavailableException` (Domain Exception)

```java
public class ComplianceBureauUnavailableException extends Exception {
    public ComplianceBureauUnavailableException(String message, Throwable cause) { ... }
}
```

Thrown by the adapter when the external service is unreachable.

### Flow Logic in Service

```
1. cacheResult = complianceCachePort.get(nationalId)
2. if (cacheResult.isPresent()) → use it, skip external call
3. else:
   a. try: externalResult = complianceBureauPort.check(nationalId)
   b. complianceCachePort.put(nationalId, externalResult)
   c. use externalResult
   d. catch ComplianceBureauUnavailableException:
      → return PipelineResult(MANUAL_REVIEW, results so far)
```

### Mapping to `ValidationResult`
```
CLEAR   → ValidationResult.pass("ComplianceBureau", "Not found in sanctions list")
FLAGGED → ValidationResult.fail("ComplianceBureau", "FLAGGED in sanctions/OFAC list")
SERVICE_UNAVAILABLE → pipeline returns MANUAL_REVIEW (no ValidationResult for this step added as "fail")
```

### Cache Behavior

| Scenario | Action |
|----------|--------|
| Cache hit, not expired | Use cached result, skip external call |
| Cache hit, expired (>24h) | Treat as miss, call external, update cache |
| Cache miss | Call external, store result in cache |
| Cache I/O error on read | Treat as miss, proceed to external call |
| Cache I/O error on write | Log warning, continue (result is still valid) |
| Service error | Do NOT cache the error — only cache CLEAR/FLAGGED |

---

## Step 3: Qualification Score

### Port Interface

```java
public interface QualificationScorePort {
    int generateScore();
}
```

### Contract

| Aspect | Detail |
|--------|--------|
| **Precondition** | Step 2 must have returned `success == true` (CLEAR, not MANUAL_REVIEW) |
| **Input** | None (internal system) |
| **Process** | Generate a random score between 0 and 100 inclusive |
| **Output** | `int` score |
| **Threshold** | Score must be **strictly greater than 60** to pass |
| **Boundary** | Score == 60 → REJECTED. Score == 61 → APPROVED. |

### Mapping to `ValidationResult`
```
score > 60  → ValidationResult.pass("QualificationScore", "Score: {score}/100 (threshold: >60)")
score <= 60 → ValidationResult.fail("QualificationScore", "Score: {score}/100 (threshold: >60, not met)")
```

---

## Short-Circuit Rules

The pipeline uses **short-circuit evaluation** — it stops as soon as a hard failure is detected:

| After Step | Condition | Action |
|------------|-----------|--------|
| 1a or 1b | Either fails | Collect all parallel results → return REJECTED |
| 2 | Service unavailable | Return MANUAL_REVIEW with results collected so far |
| 2 | FLAGGED | Return REJECTED |
| 3 | Score ≤ 60 | Return REJECTED |
| 3 | Score > 60 | Create Prospect → return APPROVED |

**Important:** Both parallel steps (1a and 1b) always execute to completion even if one fails early — we collect both results for reporting. Short-circuit only applies to subsequent sequential steps.

---

## Pipeline Output Contract

The `PipelineResult` returned by `LeadQualificationService.qualify()` always contains:

1. A `List<ValidationResult>` with **every step that executed** (in execution order).
2. A `QualificationStatus` indicating the final outcome.
3. An `Optional<Prospect>` that is present **only** when `status == APPROVED`.

### Output Scenarios

| Scenario | # Results | Status | Prospect |
|----------|-----------|--------|----------|
| Registry MISMATCH, Judicial CLEAN | 2 | REJECTED | empty |
| Registry MATCH, Judicial HAS_RECORDS | 2 | REJECTED | empty |
| Both fail | 2 | REJECTED | empty |
| Both pass, Compliance unavailable | 2 | MANUAL_REVIEW | empty |
| Both pass, Compliance FLAGGED | 3 | REJECTED | empty |
| Both pass, Compliance CLEAR, Score ≤ 60 | 4 | REJECTED | empty |
| Both pass, Compliance CLEAR, Score > 60 | 4 | APPROVED | present |
