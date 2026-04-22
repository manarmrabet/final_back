package com.example.CWMS.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import jakarta.annotation.PostConstruct;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Ghost Account Pattern — OWASP A07:2021
 *
 * Deux modes selon le profil Spring actif :
 *
 *   Profil DEV  → RedisTemplate non injecté (Redis exclu par application-dev.properties)
 *                 → fallback ConcurrentHashMap en mémoire, zéro warning
 *
 *   Profil PROD → RedisTemplate injecté (Redis configuré dans application-prod.properties)
 *                 → compteurs persistants avec TTL 24h
 *
 * Aucune modification de ce fichier n'est nécessaire pour basculer entre les deux modes.
 * Il suffit de changer spring.profiles.active dans application.properties.
 */
@Component
public class GhostAttemptStore {

    private static final int    MAX_GHOST_ATTEMPTS = 3;
    private static final String PREFIX             = "ghost:";
    private static final long   TTL_HOURS          = 24;

    // required=false : null en profil DEV (Redis exclu), injecté en profil PROD
    @Autowired(required = false)
    private org.springframework.data.redis.core.RedisTemplate<String, String> redisTemplate;

    // Fallback mémoire — toujours présent, utilisé si Redis absent
    private final ConcurrentHashMap<String, AtomicInteger> fallbackMap
            = new ConcurrentHashMap<>();

    // true une fois le warning loggué — évite la répétition à chaque appel
    private boolean warnLogged = false;

    // ── Démarrage ─────────────────────────────────────────────────────────────
    @PostConstruct
    public void init() {
        if (redisTemplate == null) {
            // Profil DEV : Redis volontairement désactivé, comportement attendu
            System.out.println("[GhostAttemptStore] Profil DEV → fallback mémoire actif (normal).");
            return;
        }
        // Profil PROD : vérifier que Redis répond vraiment
        try {
            redisTemplate.getConnectionFactory().getConnection().ping();
            System.out.println("[GhostAttemptStore] Profil PROD → Redis connecté avec succès.");
        } catch (Exception e) {
            // Redis configuré mais inaccessible : logguer une seule fois
            System.err.println("[GhostAttemptStore] ATTENTION : Redis configuré mais inaccessible."
                    + " → fallback mémoire. Vérifiez que Redis est démarré. Cause : " + e.getMessage());
            warnLogged = true;
        }
    }

    // ── API publique ──────────────────────────────────────────────────────────

    public void increment(String username) {
        try {
            if (redisTemplate != null) {
                String key   = PREFIX + username;
                Long   count = redisTemplate.opsForValue().increment(key);
                if (count != null && count == 1) {
                    redisTemplate.expire(key, TTL_HOURS, TimeUnit.HOURS);
                }
                return;
            }
        } catch (Exception e) {
            logOnce(e);
        }
        fallbackMap.computeIfAbsent(username, k -> new AtomicInteger(0)).incrementAndGet();
    }

    public boolean isGhostBlocked(String username) {
        try {
            if (redisTemplate != null) {
                String val = redisTemplate.opsForValue().get(PREFIX + username);
                if (val == null) return false;
                return Integer.parseInt(val) >= MAX_GHOST_ATTEMPTS;
            }
        } catch (Exception e) {
            logOnce(e);
        }
        AtomicInteger counter = fallbackMap.get(username);
        return counter != null && counter.get() >= MAX_GHOST_ATTEMPTS;
    }

    public int getRemainingGhostAttempts(String username) {
        try {
            if (redisTemplate != null) {
                String val  = redisTemplate.opsForValue().get(PREFIX + username);
                int    used = (val != null) ? Integer.parseInt(val) : 0;
                return Math.max(0, MAX_GHOST_ATTEMPTS - used);
            }
        } catch (Exception e) {
            logOnce(e);
        }
        AtomicInteger counter = fallbackMap.get(username);
        int used = (counter != null) ? counter.get() : 0;
        return Math.max(0, MAX_GHOST_ATTEMPTS - used);
    }

    public void reset(String username) {
        try {
            if (redisTemplate != null) {
                redisTemplate.delete(PREFIX + username);
            }
        } catch (Exception e) {
            logOnce(e);
        }
        fallbackMap.remove(username);
    }

    // ── Privé ─────────────────────────────────────────────────────────────────

    private synchronized void logOnce(Exception e) {
        if (!warnLogged) {
            System.err.println("[GhostAttemptStore] Redis inaccessible → fallback mémoire. Cause : "
                    + e.getMessage());
            warnLogged = true;
        }
    }
}