package com.exemplo.secrest.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class CodigoCacheService {

    private record CacheEntry(String code, Instant expiration) {}

    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    public void armazenar(String email, String code) {
        cache.put(email, new CacheEntry(code, Instant.now().plusSeconds(300)));
    }

    public String buscar(String email) {
        CacheEntry entry = cache.get(email);
        if (entry == null || Instant.now().isAfter(entry.expiration())) {
            cache.remove(email);
            return null;
        }
        return entry.code();
    }

    @Scheduled(fixedDelay = 60_000)
    public void limparExpirados() {
        Instant now = Instant.now();
        cache.entrySet().removeIf(e -> now.isAfter(e.getValue().expiration()));
    }
}
