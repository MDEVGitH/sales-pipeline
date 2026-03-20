# 04 — External Services Specification (Ports & Adapters)

## Overview

External services are accessed exclusively through **outbound port interfaces** defined in the application layer. The infrastructure layer provides **simulated adapters** that mimic real external systems with realistic latency and probabilistic outcomes.

This design means swapping from simulated to real HTTP-based services requires **only** writing a new adapter class — zero changes to domain or application code.

---

## Port → Adapter → DTO Mapping

| Port (Application Layer) | Adapter (Infrastructure) | DTO (Adapter-internal) | Mapper |
|--------------------------|--------------------------|------------------------|--------|
| `RegistryPort` | `SimulatedRegistryAdapter` | `RegistryApiResponse` | `RegistryMapper` |
| `JudicialPort` | `SimulatedJudicialAdapter` | `JudicialApiResponse` | `JudicialMapper` |
| `ComplianceBureauPort` | `SimulatedComplianceBureauAdapter` | `ComplianceApiResponse` | `ComplianceMapper` |
| `QualificationScorePort` | `RandomScoreAdapter` | — (returns primitive `int`) | — |
| `ComplianceCachePort` | `FileComplianceCacheAdapter` | `CacheEntryDto` | `CacheMapper` |

**Data flow inside each adapter:**
```
Adapter.check(domain args)
  → simulate external call → build DTO (as if deserialized from JSON/HTTP)
  → Mapper.toDomain(dto) → return domain type
```
DTOs never leave the adapter package. The port interface only speaks in domain types.

---

## Adapter 1: `SimulatedRegistryAdapter`

**Implements:** `RegistryPort`

**Internal flow:**
```
check(Lead lead) → RegistryCheckResult
  1. Simulate latency: Thread.sleep(random 200-800ms)
  2. Generate random outcome → build RegistryApiResponse (DTO):
     - 70% → RegistryApiResponse(found=true, matching name/birthdate, matchScore=0.95)
     - 20% → RegistryApiResponse(found=false, nulls, matchScore=0.0)
     - 10% → RegistryApiResponse(found=true, different name/birthdate, matchScore=0.3)
  3. RegistryMapper.toDomain(dto, lead) → RegistryCheckResult (domain)
  4. Return domain object
```

**`RegistryApiResponse` (DTO):**
```java
public record RegistryApiResponse(
    boolean found,
    String firstName,       // as returned by external system (may differ from lead)
    String lastName,
    String birthdate,       // String "YYYY-MM-DD" from external API
    double matchScore       // external system's confidence 0.0-1.0
) {}
```

**`RegistryMapper`:**
```java
public class RegistryMapper {
    public RegistryCheckResult toDomain(RegistryApiResponse response, Lead lead) {
        if (!response.found()) {
            return new RegistryCheckResult(RegistryStatus.NOT_FOUND,
                "National ID not found in registry");
        }
        boolean nameMatches = lead.firstName().equalsIgnoreCase(response.firstName())
                           && lead.lastName().equalsIgnoreCase(response.lastName());
        boolean birthdateMatches = lead.birthdate().equals(LocalDate.parse(response.birthdate()));

        if (nameMatches && birthdateMatches) {
            return new RegistryCheckResult(RegistryStatus.MATCH,
                "Person verified in national registry");
        }
        return new RegistryCheckResult(RegistryStatus.MISMATCH,
            buildMismatchDetail(response, lead));
    }
}
```

**Latency simulation:**
```java
long latency = ThreadLocalRandom.current().nextLong(200, 801); // 200-800ms inclusive
Thread.sleep(latency);
```

**Deterministic mode (for testing):**
The adapter constructor accepts an optional `seed` parameter for `Random`. When provided, outcomes are reproducible. When omitted, uses `ThreadLocalRandom`.

---

## Adapter 2: `SimulatedJudicialAdapter`

**Implements:** `JudicialPort`

**Internal flow:**
```
check(String nationalId) → JudicialCheckResult
  1. Simulate latency: Thread.sleep(random 300-1000ms)
  2. Generate random outcome → build JudicialApiResponse (DTO):
     - 85% → JudicialApiResponse(hasRecords=false, recordCount=0)
     - 15% → JudicialApiResponse(hasRecords=true, recordCount=random 1-5)
  3. JudicialMapper.toDomain(dto) → JudicialCheckResult (domain)
  4. Return domain object
```

**`JudicialApiResponse` (DTO):**
```java
public record JudicialApiResponse(
    boolean hasRecords,
    int recordCount       // external system may return count of records
) {}
```

**`JudicialMapper`:**
```java
public class JudicialMapper {
    public JudicialCheckResult toDomain(JudicialApiResponse response) {
        JudicialStatus status = response.hasRecords()
            ? JudicialStatus.HAS_RECORDS
            : JudicialStatus.CLEAN;
        return new JudicialCheckResult(status);
    }
}
```

**Latency simulation:**
```java
long latency = ThreadLocalRandom.current().nextLong(300, 1001); // 300-1000ms inclusive
Thread.sleep(latency);
```

---

## Adapter 3: `SimulatedComplianceBureauAdapter`

**Implements:** `ComplianceBureauPort`

**Internal flow:**
```
check(String nationalId) → ComplianceCheckResult
                            throws ComplianceBureauUnavailableException
  1. Simulate latency: Thread.sleep(random 100-500ms)
  2. Generate random outcome → build ComplianceApiResponse (DTO) or throw:
     - 80% → ComplianceApiResponse(status="CLEAR", checkedAt=now, source="OFAC")
     - 10% → ComplianceApiResponse(status="FLAGGED", checkedAt=now, source="OFAC")
     - 10% → throw ComplianceBureauUnavailableException("Compliance Bureau service unavailable")
  3. ComplianceMapper.toDomain(dto) → ComplianceCheckResult (domain)
  4. Return domain object
```

**`ComplianceApiResponse` (DTO):**
```java
public record ComplianceApiResponse(
    String status,        // "CLEAR", "FLAGGED" — raw String from external API, not our enum
    String checkedAt,     // ISO-8601 timestamp from external system
    String source         // "OFAC", "EU_SANCTIONS", etc.
) {}
```

**`ComplianceMapper`:**
```java
public class ComplianceMapper {
    public ComplianceCheckResult toDomain(ComplianceApiResponse response) {
        ComplianceStatus status = ComplianceStatus.valueOf(response.status().toUpperCase());
        return new ComplianceCheckResult(status);
    }
}
```

**Important:** The exception case simulates a service outage. The adapter throws a **checked exception** (`ComplianceBureauUnavailableException`) so the service layer is forced to handle it explicitly. The exception is thrown **before** building a DTO — there's nothing to map when the service is down.

**Latency simulation:**
```java
long latency = ThreadLocalRandom.current().nextLong(100, 501); // 100-500ms inclusive
Thread.sleep(latency);
```

---

## Adapter 4: `RandomScoreAdapter`

**Implements:** `QualificationScorePort`

**Behavior:**
```
generateScore() → int
  1. Return ThreadLocalRandom.current().nextInt(0, 101) // 0-100 inclusive
  2. No latency simulation (internal system)
```

**Deterministic mode (for testing):**
Accepts optional `seed` for reproducible scores.

---

## Adapter 5: `FileComplianceCacheAdapter`

**Implements:** `ComplianceCachePort`

This adapter uses a **DTO (`CacheEntryDto`)** for JSON serialization and a **mapper (`CacheMapper`)** to convert between the DTO and domain types. Gson never touches domain objects directly.

### Internal flow

**`get(String nationalId) → Optional<ComplianceCheckResult>`**
```
1. If cache file does not exist → return Optional.empty()
2. Read cache file (JSON) → Map<String, CacheEntryDto>
3. Look up nationalId in map
4. If not found → return Optional.empty()
5. If CacheMapper.isExpired(dto, ttlHours) → return Optional.empty()
6. CacheMapper.toDomain(dto) → ComplianceCheckResult
7. Return Optional.of(domainResult)
```

**`put(String nationalId, ComplianceCheckResult result) → void`**
```
1. CacheMapper.toDto(result) → CacheEntryDto (sets timestamp to now)
2. Read existing cache file → Map<String, CacheEntryDto> (or create empty map)
3. Add/update entry: nationalId → dto
4. Serialize map to JSON via Gson
5. Write to file, create parent directories if needed
```

### `CacheEntryDto` (DTO)
```java
public class CacheEntryDto {
    public String status;       // "CLEAR" or "FLAGGED" — raw String, not enum
    public String timestamp;    // ISO-8601 string
    // Public fields + implicit no-arg constructor for Gson compatibility
}
```

### `CacheMapper`
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

### Configuration

| Parameter | Default | Description |
|-----------|---------|-------------|
| `filePath` | `./data/compliance-cache.json` | Cache file location |
| `ttlHours` | `24` | Time-to-live in hours |

### File Format (CacheEntryDto serialized)

```json
{
  "123456789": {
    "status": "CLEAR",
    "timestamp": "2026-03-20T14:30:00"
  },
  "987654321": {
    "status": "FLAGGED",
    "timestamp": "2026-03-19T10:15:00"
  }
}
```

### Error Handling

| Error | Handling |
|-------|----------|
| File not found on read | Return empty (not an error) |
| JSON parse error (corrupted DTO) | Log warning, return empty (treat as cache miss) |
| Invalid enum value in DTO | Log warning, return empty (defensive mapping) |
| File write error | Log warning, don't crash (cache is optimization, not critical path) |
| Concurrent access | `synchronized` on read/write methods (single JVM process) |

### Thread Safety

Since this is a CLI application (single JVM, multiple threads via CompletableFuture), `synchronized` on the `get` and `put` methods is sufficient. No need for distributed locking.

---

## Latency Summary

| Service | Min Latency | Max Latency | Failure Mode |
|---------|-------------|-------------|--------------|
| Registry | 200ms | 800ms | None (always responds) |
| Judicial | 300ms | 1000ms | None (always responds) |
| Compliance | 100ms | 500ms | 10% throws exception |
| Score | 0ms | 0ms | None (internal) |
| Cache read | ~0ms | ~5ms | Degraded to miss |
| Cache write | ~0ms | ~10ms | Silent failure |

---

## Observability (Logging)

Each adapter logs its activity with a consistent prefix for traceability:

| Adapter | Log Prefix | Example |
|---------|-----------|---------|
| Registry | `[REGISTRY]` | `[REGISTRY] Checking nationalId=123... MATCH (450ms)` |
| Judicial | `[JUDICIAL]` | `[JUDICIAL] Checking nationalId=123... CLEAN (720ms)` |
| Compliance | `[COMPLIANCE]` | `[COMPLIANCE] Cache HIT for nationalId=123` |
| Compliance | `[COMPLIANCE]` | `[COMPLIANCE] Service call for nationalId=123... CLEAR (230ms)` |
| Compliance | `[COMPLIANCE]` | `[COMPLIANCE] Service UNAVAILABLE for nationalId=123` |
| Score | `[SCORE]` | `[SCORE] Generated score: 78/100 (threshold: >60)` |

**Log format:** `[PREFIX] Action for context... RESULT (latency)`

All logging goes to `System.out.println` — no logging framework dependency.
