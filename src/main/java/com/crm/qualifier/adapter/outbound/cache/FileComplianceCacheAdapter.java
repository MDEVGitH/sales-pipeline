package com.crm.qualifier.adapter.outbound.cache;

import com.crm.qualifier.application.port.outbound.ComplianceCachePort;
import com.crm.qualifier.domain.model.ComplianceCheckResult;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class FileComplianceCacheAdapter implements ComplianceCachePort {

    private final String filePath;
    private final long ttlHours;
    private final Gson gson;
    private final CacheMapper mapper = new CacheMapper();
    private static final Type CACHE_TYPE = new TypeToken<Map<String, CacheEntryDto>>() {}.getType();

    public FileComplianceCacheAdapter(String filePath, long ttlHours) {
        this.filePath = filePath;
        this.ttlHours = ttlHours;
        this.gson = new GsonBuilder().setPrettyPrinting().create();
    }

    @Override
    public synchronized Optional<ComplianceCheckResult> get(String nationalId) {
        try {
            Path path = Paths.get(filePath);
            if (!Files.exists(path)) {
                return Optional.empty();
            }

            String json = Files.readString(path);
            Map<String, CacheEntryDto> cache = gson.fromJson(json, CACHE_TYPE);

            if (cache == null || !cache.containsKey(nationalId)) {
                return Optional.empty();
            }

            CacheEntryDto dto = cache.get(nationalId);
            if (mapper.isExpired(dto, ttlHours)) {
                return Optional.empty();
            }

            return Optional.of(mapper.toDomain(dto));

        } catch (Exception e) {
            System.out.println("[COMPLIANCE] Cache read error: " + e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public synchronized void put(String nationalId, ComplianceCheckResult result) {
        try {
            Path path = Paths.get(filePath);
            Map<String, CacheEntryDto> cache = new HashMap<>();

            if (Files.exists(path)) {
                try {
                    String json = Files.readString(path);
                    Map<String, CacheEntryDto> existing = gson.fromJson(json, CACHE_TYPE);
                    if (existing != null) {
                        cache.putAll(existing);
                    }
                } catch (Exception e) {
                    // Corrupted file — start fresh
                    System.out.println("[COMPLIANCE] Cache file corrupted, starting fresh: " + e.getMessage());
                }
            }

            CacheEntryDto dto = mapper.toDto(result);
            cache.put(nationalId, dto);

            // Create parent directories if needed
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }

            Files.writeString(path, gson.toJson(cache, CACHE_TYPE));

        } catch (IOException e) {
            System.out.println("[COMPLIANCE] Cache write error: " + e.getMessage());
        }
    }
}
