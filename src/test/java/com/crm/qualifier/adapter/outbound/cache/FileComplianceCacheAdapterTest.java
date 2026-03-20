package com.crm.qualifier.adapter.outbound.cache;

import com.crm.qualifier.domain.model.ComplianceCheckResult;
import com.crm.qualifier.domain.model.ComplianceCheckResult.ComplianceStatus;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
class FileComplianceCacheAdapterTest {

    @TempDir
    Path tempDir;

    private FileComplianceCacheAdapter createAdapter(long ttlHours) {
        String path = tempDir.resolve("cache.json").toString();
        return new FileComplianceCacheAdapter(path, ttlHours);
    }

    @Test
    void shouldReturnEmpty_whenCacheFileDoesNotExist() {
        FileComplianceCacheAdapter adapter = createAdapter(24);
        Optional<ComplianceCheckResult> result = adapter.get("123");
        assertTrue(result.isEmpty());
    }

    @Test
    void shouldReturnEmpty_whenKeyNotInCache() {
        FileComplianceCacheAdapter adapter = createAdapter(24);
        adapter.put("other-key", new ComplianceCheckResult(ComplianceStatus.CLEAR));
        Optional<ComplianceCheckResult> result = adapter.get("123");
        assertTrue(result.isEmpty());
    }

    @Test
    void shouldStoreAndRetrieve_clearResult() {
        FileComplianceCacheAdapter adapter = createAdapter(24);
        adapter.put("123", new ComplianceCheckResult(ComplianceStatus.CLEAR));
        Optional<ComplianceCheckResult> result = adapter.get("123");
        assertTrue(result.isPresent());
        assertEquals(ComplianceStatus.CLEAR, result.get().status());
    }

    @Test
    void shouldStoreAndRetrieve_flaggedResult() {
        FileComplianceCacheAdapter adapter = createAdapter(24);
        adapter.put("123", new ComplianceCheckResult(ComplianceStatus.FLAGGED));
        Optional<ComplianceCheckResult> result = adapter.get("123");
        assertTrue(result.isPresent());
        assertEquals(ComplianceStatus.FLAGGED, result.get().status());
    }

    @Test
    void shouldReturnEmpty_whenEntryExpired() throws Exception {
        // Use a very short TTL so the entry expires
        String path = tempDir.resolve("cache-expire.json").toString();
        FileComplianceCacheAdapter adapter = new FileComplianceCacheAdapter(path, 0); // 0 hours TTL
        adapter.put("123", new ComplianceCheckResult(ComplianceStatus.CLEAR));
        // With TTL=0, the entry should be considered expired immediately (or within a second)
        Thread.sleep(10);
        Optional<ComplianceCheckResult> result = adapter.get("123");
        assertTrue(result.isEmpty(), "Entry with TTL=0 should be expired");
    }

    @Test
    void shouldOverwriteExistingEntry() {
        FileComplianceCacheAdapter adapter = createAdapter(24);
        adapter.put("123", new ComplianceCheckResult(ComplianceStatus.CLEAR));
        adapter.put("123", new ComplianceCheckResult(ComplianceStatus.FLAGGED));
        Optional<ComplianceCheckResult> result = adapter.get("123");
        assertTrue(result.isPresent());
        assertEquals(ComplianceStatus.FLAGGED, result.get().status());
    }

    @Test
    void shouldCreateDirectoriesIfNotExist() {
        String nestedPath = tempDir.resolve("nested/dir/cache.json").toString();
        FileComplianceCacheAdapter adapter = new FileComplianceCacheAdapter(nestedPath, 24);
        adapter.put("123", new ComplianceCheckResult(ComplianceStatus.CLEAR));
        assertTrue(Files.exists(Path.of(nestedPath)));
    }

    @Test
    void shouldHandleCorruptedCacheFile() throws Exception {
        Path cacheFile = tempDir.resolve("corrupted.json");
        Files.writeString(cacheFile, "{{invalid json}}");
        FileComplianceCacheAdapter adapter = new FileComplianceCacheAdapter(cacheFile.toString(), 24);
        Optional<ComplianceCheckResult> result = adapter.get("123");
        assertTrue(result.isEmpty(), "Should return empty for corrupted cache");
    }

    @Test
    void shouldPersistAcrossInstances() {
        String path = tempDir.resolve("persist.json").toString();
        FileComplianceCacheAdapter adapterA = new FileComplianceCacheAdapter(path, 24);
        adapterA.put("123", new ComplianceCheckResult(ComplianceStatus.CLEAR));

        FileComplianceCacheAdapter adapterB = new FileComplianceCacheAdapter(path, 24);
        Optional<ComplianceCheckResult> result = adapterB.get("123");
        assertTrue(result.isPresent());
        assertEquals(ComplianceStatus.CLEAR, result.get().status());
    }

    @Test
    void shouldStoreMultipleEntries() {
        FileComplianceCacheAdapter adapter = createAdapter(24);
        adapter.put("111", new ComplianceCheckResult(ComplianceStatus.CLEAR));
        adapter.put("222", new ComplianceCheckResult(ComplianceStatus.FLAGGED));
        adapter.put("333", new ComplianceCheckResult(ComplianceStatus.CLEAR));

        assertTrue(adapter.get("111").isPresent());
        assertTrue(adapter.get("222").isPresent());
        assertTrue(adapter.get("333").isPresent());
        assertEquals(ComplianceStatus.CLEAR, adapter.get("111").get().status());
        assertEquals(ComplianceStatus.FLAGGED, adapter.get("222").get().status());
        assertEquals(ComplianceStatus.CLEAR, adapter.get("333").get().status());
    }
}
