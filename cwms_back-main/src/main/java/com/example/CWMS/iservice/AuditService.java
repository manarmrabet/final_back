package com.example.CWMS.iservice;

import com.example.CWMS.model.cwms.AuditLog;
import jakarta.servlet.http.HttpServletRequest;

public interface AuditService {
    void logLogin(String username, String ip, String userAgent,
                  boolean success, String sessionId);
    void logLogout(String username, String ip, String sessionId);
    void logAction(String action, String entityType, String entityId,
                   Object oldObj, Object newObj);
    void logError(Exception ex, HttpServletRequest request, int statusCode);
    void logHttpError(HttpServletRequest request, int statusCode, long durationMs);
    void enrichWithCurrentUser(AuditLog.AuditLogBuilder builder);
    void save(AuditLog auditLog);
    String toJson(Object obj);
    AuditLog.EventType resolveEventType(String action);
    String extractClientIp(HttpServletRequest request);
    String truncateStackTrace(Exception ex);
}