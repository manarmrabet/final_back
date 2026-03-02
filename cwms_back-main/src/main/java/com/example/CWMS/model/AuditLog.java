package com.example.CWMS.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "audit_logs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 50)
    private EventType eventType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Severity severity;

    // ✅ FK vers votre Users(UserId) — Integer comme dans votre User.java
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", referencedColumnName = "UserId")
    private User user;

    // Snapshot dénormalisé (reste lisible même si user supprimé)
    @Column(length = 100)
    private String username;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "user_agent", length = 500)
    private String userAgent;

    @Column(name = "http_method", length = 10)
    private String httpMethod;

    @Column(length = 500)
    private String endpoint;

    @Column(length = 200)
    private String action;

    @Column(name = "entity_type", length = 100)
    private String entityType;

    @Column(name = "entity_id", length = 100)
    private String entityId;

    @Column(name = "old_value", columnDefinition = "NVARCHAR(MAX)")
    private String oldValue;

    @Column(name = "new_value", columnDefinition = "NVARCHAR(MAX)")
    private String newValue;

    @Column(name = "status_code")
    private Integer statusCode;

    @Column(name = "error_message", columnDefinition = "NVARCHAR(MAX)")
    private String errorMessage;

    @Column(name = "stack_trace", columnDefinition = "NVARCHAR(MAX)")
    private String stackTrace;

    @Column(name = "duration_ms")
    private Long durationMs;

    @Column(name = "session_id", length = 200)
    private String sessionId;

    @Column(name = "correlation_id", length = 100)
    private String correlationId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public enum EventType {
        LOGIN, LOGOUT, LOGIN_FAILED,
        CREATE, UPDATE, DELETE, READ,
        ERROR, EXPORT, IMPORT
    }

    public enum Severity {
        INFO, WARNING, ERROR, CRITICAL
    }
}