# 01 — Domain Model Specification

## Overview

The domain model represents the core business concepts of the lead qualification pipeline.
All domain objects live in the **innermost ring** of the hexagonal architecture — they have **zero dependencies** on infrastructure, frameworks, or external libraries.

---

## Entities & Value Objects

### 1. `Lead` (Value Object)

Represents a sales-qualified lead entering the qualification pipeline.

| Field         | Type          | Constraints                                                        |
|---------------|---------------|--------------------------------------------------------------------|
| `nationalId`  | `String`      | Non-null, non-blank, alphanumeric, 6-20 characters                 |
| `birthdate`   | `LocalDate`   | Non-null, must be in the past (strictly before today), age ≥ 18    |
| `firstName`   | `String`      | Non-null, non-blank, 1-100 characters, letters and spaces only     |
| `lastName`    | `String`      | Non-null, non-blank, 1-100 characters, letters and spaces only     |
| `email`       | `String`      | Non-null, non-blank, must match basic email pattern `^[^@]+@[^@]+\.[^@]+$` |

**Invariants:**
- A `Lead` is immutable once created (Java `record`).
- All validations happen in the compact constructor — a `Lead` instance is **always valid by construction**.
- `birthdate` implies the person must be at least 18 years old at the time of qualification.
- `nationalId` uniquely identifies a lead across the system.

**Design rationale:**
Using a Value Object (record) rather than an Entity because leads are identified by their `nationalId` value, not by a mutable lifecycle identity. Two leads with the same `nationalId` are the same lead.

---

### 2. `Prospect` (Value Object)

Represents a lead that has successfully passed all qualification steps.

| Field                | Type              | Constraints                                      |
|----------------------|-------------------|--------------------------------------------------|
| `nationalId`         | `String`          | Inherited from Lead                              |
| `birthdate`          | `LocalDate`       | Inherited from Lead                              |
| `firstName`          | `String`          | Inherited from Lead                              |
| `lastName`           | `String`          | Inherited from Lead                              |
| `email`              | `String`          | Inherited from Lead                              |
| `qualificationScore` | `int`             | 61-100 inclusive (only created when score > 60)   |
| `qualifiedAt`        | `LocalDateTime`   | Non-null, must be ≤ now                          |

**Factory method:**
```
Prospect.fromLead(Lead lead, int score) → Prospect
```
- Copies all fields from Lead.
- Sets `qualifiedAt` to `LocalDateTime.now()`.
- Validates `score` is in range 61-100.

**Design rationale:**
A `Prospect` is never constructed directly from raw data — it always originates from a `Lead` that passed qualification. The factory method enforces this invariant at compile time.

---

### 3. `ValidationResult` (Value Object)

Represents the outcome of a single validation step.

| Field           | Type              | Constraints                               |
|-----------------|-------------------|-------------------------------------------|
| `success`       | `boolean`         | `true` if validation passed                |
| `validatorName` | `String`          | Non-null, non-blank, identifies the step   |
| `message`       | `String`          | Non-null, human-readable detail            |
| `timestamp`     | `LocalDateTime`   | Non-null, when the validation ran          |

**Factory methods (static):**
```
ValidationResult.pass(String validatorName, String message) → ValidationResult
ValidationResult.fail(String validatorName, String message) → ValidationResult
```
- Both set `timestamp` to `LocalDateTime.now()`.
- Prevents callers from building inconsistent results.

---

### 4. `QualificationStatus` (Enum)

```
APPROVED       — All validations passed, lead converted to prospect.
REJECTED       — At least one hard validation failed.
MANUAL_REVIEW  — Compliance bureau was unavailable; human must decide.
```

---

### 5. `PipelineResult` (Value Object)

Represents the aggregate outcome of the entire qualification pipeline.

| Field               | Type                       | Constraints                                          |
|---------------------|----------------------------|------------------------------------------------------|
| `validationResults` | `List<ValidationResult>`   | Non-null, unmodifiable, 1-4 entries                   |
| `status`            | `QualificationStatus`      | Non-null                                              |
| `prospect`          | `Prospect` (nullable)      | Non-null only when `status == APPROVED`               |

**Invariants:**
- If `status == APPROVED`, `prospect` must be non-null.
- If `status != APPROVED`, `prospect` must be null.
- `validationResults` reflects every step that actually executed (1 to 4, depending on short-circuit).

**Accessor:**
```
Optional<Prospect> getProspect() — safe accessor for nullable prospect field.
```

---

## Domain Rules Summary

| Rule | Description |
|------|-------------|
| DR-1 | A Lead is always valid upon construction (fail-fast in constructor) |
| DR-2 | A Prospect can only be created from a Lead via the factory method |
| DR-3 | Qualification score for a Prospect is always 61-100 |
| DR-4 | PipelineResult is consistent: APPROVED ↔ prospect present |
| DR-5 | All domain objects are immutable (records) |
| DR-6 | Domain objects have NO dependencies on infrastructure or frameworks |
