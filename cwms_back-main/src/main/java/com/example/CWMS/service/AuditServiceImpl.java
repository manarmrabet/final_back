package com.example.CWMS.service;

import com.example.CWMS.iservice.AuditService;
import com.example.CWMS.model.cwms.AuditLog;
import com.example.CWMS.model.cwms.AuditLog.EventType;
import com.example.CWMS.model.cwms.AuditLog.Severity;
import com.example.CWMS.model.cwms.User;
import com.example.CWMS.repository.cwms.AuditLogRepository;
import com.example.CWMS.repository.cwms.UserRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuditServiceImpl implements AuditService {

    private final AuditLogRepository auditLogRepository;
    private final UserRepository     userRepository;
    private final ObjectMapper       objectMapper;

    // ── CONNEXIONS ────────────────────────────────────────────
//loglogin et loglogout enregistre les accès au système.
// Capture l'IP, l'User-Agent et si la tentative a réussi
    @Override
    public void logLogin(String username, String ip, String userAgent,
                         boolean success, String sessionId) {
        User user = userRepository.findByUsername(username).orElse(null);

        save(AuditLog.builder()
                .eventType (success ? EventType.LOGIN : EventType.LOGIN_FAILED)
                .severity  (success ? Severity.INFO   : Severity.WARNING)
                .action    (success ? "CONNEXION_OK"  : "TENTATIVE_ECHOUEE")
                .user      (user)
                .username  (username)
                .ipAddress (ip)
                .userAgent (userAgent)
                .sessionId (sessionId)
                .statusCode(success ? 200 : 401)
                .build());
    }

    @Override
    public void logLogout(String username, String ip, String sessionId) {
        User user = userRepository.findByUsername(username).orElse(null);

        save(AuditLog.builder()
                .eventType(EventType.LOGOUT)
                .severity (Severity.INFO)
                .action   ("DECONNEXION")
                .user     (user)
                .username (username)
                .ipAddress(ip)
                .sessionId(sessionId)
                .build());
    }

    // ── ACTIONS CRITIQUES ─────────────────────────────────────

    @Override
    public void logAction(String action, String entityType, String entityId,
                          Object oldObj, Object newObj) {
        try {
            AuditLog.AuditLogBuilder builder = AuditLog.builder()
                    .eventType    (resolveEventType(action))
                    .severity     (Severity.INFO)
                    .action       (action)
                    .entityType   (entityType)
                    .entityId     (entityId)
                    .oldValue     (toJson(oldObj))
                    .newValue     (toJson(newObj))
                    .correlationId(UUID.randomUUID().toString());

            enrichWithCurrentUser(builder);
            save(builder.build());

        } catch (Exception e) {
            log.error("Erreur log action: {}", e.getMessage());
        }
    }

    /**
     * ✅ Variante de logAction qui accepte un username en snapshot.
     * Utile après suppression d'un user (on ne peut plus faire findByUsername).
     */
    public void logActionWithUsername(String action, String entityType, String entityId,
                                      String snapshotUsername, Object oldObj, Object newObj) {
        try {
            AuditLog.AuditLogBuilder builder = AuditLog.builder()
                    .eventType    (resolveEventType(action))
                    .severity     (Severity.INFO)
                    .action       (action)
                    .entityType   (entityType)
                    .entityId     (entityId)
                    .username     (snapshotUsername)   // snapshot dénormalisé
                    .oldValue     (toJson(oldObj))
                    .newValue     (toJson(newObj))
                    .correlationId(UUID.randomUUID().toString());

            // On enrichit avec l'admin qui effectue l'action (pas le user supprimé)
            enrichWithCurrentUser(builder);
            save(builder.build());

        } catch (Exception e) {
            log.error("Erreur logActionWithUsername: {}", e.getMessage());
        }
    }

    /**
     * ✅ Log une tentative de création échouée (email doublon, username doublon...).
     * S'exécute dans une transaction SÉPARÉE pour ne pas être rollbacké
     * avec la transaction principale qui échoue.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logFailedCreation(String targetValue, String reason, String details) {
        try {
            AuditLog.AuditLogBuilder builder = AuditLog.builder()
                    .eventType    (EventType.CREATE_FAILED)
                    .severity     (Severity.WARNING)
                    .action       ("USER_CREATE_FAILED")
                    .entityType   ("User")
                    .entityId     (targetValue)
                    .errorMessage (details)
                    .oldValue     (reason)
                    .correlationId(UUID.randomUUID().toString());

            enrichWithCurrentUser(builder);
            save(builder.build());

        } catch (Exception e) {
            log.error("Erreur logFailedCreation: {}", e.getMessage());
        }
    }

    // ── ERREURS ───────────────────────────────────────────────

    @Override
    public void logError(Exception ex, HttpServletRequest request, int statusCode) {
        AuditLog.AuditLogBuilder builder = AuditLog.builder()
                .eventType   (EventType.ERROR)
                .severity    (statusCode >= 500 ? Severity.CRITICAL : Severity.ERROR)
                .ipAddress   (extractClientIp(request))
                .httpMethod  (request.getMethod())
                .endpoint    (request.getRequestURI())
                .statusCode  (statusCode)
                .errorMessage(ex.getMessage())
                .stackTrace  (truncateStackTrace(ex));

        enrichWithCurrentUser(builder);
        save(builder.build());
    }

    @Override
    public void logHttpError(HttpServletRequest request, int statusCode, long durationMs) {
        AuditLog.AuditLogBuilder builder = AuditLog.builder()
                .eventType (EventType.ERROR)
                .severity  (statusCode >= 500 ? Severity.CRITICAL : Severity.ERROR)
                .ipAddress (extractClientIp(request))
                .httpMethod(request.getMethod())
                .endpoint  (request.getRequestURI())
                .statusCode(statusCode)
                .durationMs(durationMs);

        enrichWithCurrentUser(builder);
        save(builder.build());
    }

    // ── UTILITAIRES ───────────────────────────────────────────

    @Override
    public void enrichWithCurrentUser(AuditLog.AuditLogBuilder builder) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated()
                    && !"anonymousUser".equals(auth.getPrincipal())) {
                String username = auth.getName();
                builder.username(username);
                // ✅ Vérification id non null pour éviter le crash session Hibernate
                userRepository.findByUsername(username).ifPresent(u -> {
                    if (u.getUserId() != null) {
                        builder.user(u);
                    }
                });
            }
        } catch (Exception e) {
            log.warn("enrichWithCurrentUser ignoré: {}", e.getMessage());
        }
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void save(AuditLog auditLog) {
        try {
            AuditLog saved = auditLogRepository.save(auditLog);
            log.info("✅ Audit — id={} type={} user={}",
                    saved.getId(), saved.getEventType(), saved.getUsername());
        } catch (Exception e) {
            log.error("❌ Erreur sauvegarde audit: {}", e.getMessage(), e);
        }
    }

    @Override
    public String toJson(Object obj) {
        if (obj == null) return null;
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            return obj.toString();
        }
    }

    @Override
    public EventType resolveEventType(String action) {
        if (action == null) return EventType.READ;
        String a = action.toUpperCase();
        if (a.contains("CREATE") || a.contains("ADD")    || a.contains("SAVE"))    return EventType.CREATE;
        if (a.contains("UPDATE") || a.contains("EDIT")   || a.contains("MODIF"))   return EventType.UPDATE;
        if (a.contains("DELETE") || a.contains("REMOVE") || a.contains("SUPPRIM")) return EventType.DELETE;
        if (a.contains("FAILED") || a.contains("ECHEC"))                           return EventType.CREATE_FAILED;
        return EventType.READ;
    }

    @Override
    public String extractClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        return xff != null ? xff.split(",")[0].trim() : request.getRemoteAddr();
    }

    @Override
    public String truncateStackTrace(Exception ex) {
        StringWriter sw = new StringWriter();
        ex.printStackTrace(new PrintWriter(sw));
        String t = sw.toString();
        return t.length() > 4000 ? t.substring(0, 4000) + "..." : t;
    }
}