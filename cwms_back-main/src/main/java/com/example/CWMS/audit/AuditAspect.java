package com.example.CWMS.audit;

import com.example.CWMS.iservice.AuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.context.ApplicationContext;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Component;

@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class AuditAspect {

    private final AuditService       auditService;
    private final ApplicationContext applicationContext;

    @Around("@annotation(auditable)")
    public Object audit(ProceedingJoinPoint joinPoint,
                        Auditable auditable) throws Throwable {

        // ✅ Capturer l'ancienne valeur AVANT l'exécution
        Object oldValue = captureOldValue(joinPoint, auditable);

        try {
            Object result = joinPoint.proceed();

            auditService.logAction(
                    auditable.action(),
                    auditable.entityType(),
                    extractId(joinPoint, result),
                    oldValue,
                    result
            );

            return result;

        } catch (Exception ex) {
            auditService.logAction(
                    auditable.action() + "_FAILED",
                    auditable.entityType(),
                    null, null, null
            );
            throw ex;
        }
    }

    // ─────────────────────────────────────────────────────────
    // Capture l'ancienne valeur AVANT modification/suppression
    // ─────────────────────────────────────────────────────────
    private Object captureOldValue(ProceedingJoinPoint joinPoint,
                                   Auditable auditable) {
        String action = auditable.action().toUpperCase();

        boolean isUpdateOrDelete = action.contains("UPDATE")
                || action.contains("EDIT")
                || action.contains("DELETE")
                || action.contains("MODIF")
                || action.contains("SUPPRIM");

        if (!isUpdateOrDelete) return null;

        try {
            Integer id = extractIdFromArgs(joinPoint.getArgs());
            if (id == null) {
                log.warn("⚠️ Aucun ID trouvé pour {}", auditable.action());
                return null;
            }

            // ✅ Convention : "MenuItem" → "menuItemRepository"
            //                "User"     → "userRepository"
            //                "Role"     → "roleRepository"
            String entityType = auditable.entityType();
            String repoName   = Character.toLowerCase(entityType.charAt(0))
                    + entityType.substring(1)
                    + "Repository";

            log.debug("🔍 Recherche repository: {}", repoName);

            @SuppressWarnings("unchecked")
            JpaRepository<Object, Object> repo =
                    (JpaRepository<Object, Object>) applicationContext.getBean(repoName);

            Object oldValue = repo.findById(id).orElse(null);

            if (oldValue != null) {
                log.debug("✅ Ancienne valeur capturée pour id={}", id);
            }

            return oldValue;

        } catch (Exception e) {
            log.warn("⚠️ Impossible de capturer l'ancienne valeur: {}", e.getMessage());
            return null;
        }
    }

    // ─────────────────────────────────────────────────────────
    // Extrait l'ID depuis le résultat ou les arguments
    // ─────────────────────────────────────────────────────────
    private String extractId(ProceedingJoinPoint joinPoint, Object result) {

        // 1. Depuis le résultat (CREATE → DTO retourné contient l'ID)
        if (result != null) {
            try {
                Object id = result.getClass()
                        .getMethod("getId")
                        .invoke(result);
                if (id != null) return id.toString();
            } catch (Exception ignored) {}
        }

        // 2. Depuis les arguments (UPDATE/DELETE → 1er arg Integer/Long = ID)
        Integer id = extractIdFromArgs(joinPoint.getArgs());
        return id != null ? id.toString() : null;
    }

    // ─────────────────────────────────────────────────────────
    // Cherche le 1er argument Integer ou Long dans les args
    // Convention : updateUser(Integer id, UserDTO dto) → args[0] = id
    //              deleteUser(Integer id)               → args[0] = id
    // ─────────────────────────────────────────────────────────
    private Integer extractIdFromArgs(Object[] args) {
        if (args == null || args.length == 0) return null;

        for (Object arg : args) {
            if (arg instanceof Integer) return (Integer) arg;
            if (arg instanceof Long)    return ((Long) arg).intValue();
        }

        return null;
    }
}