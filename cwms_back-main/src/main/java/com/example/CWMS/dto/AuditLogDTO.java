package com.example.CWMS.dto;

import com.example.CWMS.model.AuditLog;
import com.example.CWMS.model.User;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;


import lombok.*;


@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLogDTO {

    private Long          id;
    private String        eventType;
    private String        severity;

    // ✅ Données User extraites — adaptées à votre UserDTO (champs en PascalCase)
    private Integer       userId;
    private String        username;
    private String        userFullName;
    private String        userRole;
    private String        userSite;

    private String        ipAddress;
    private String        httpMethod;
    private String        endpoint;
    private String        action;
    private String        entityType;
    private String        entityId;
    private String        oldValue;
    private String        newValue;
    private Integer       statusCode;
    private String        errorMessage;
    private Long          durationMs;
    private String        sessionId;
    private LocalDateTime createdAt;

    // ✅ Mapper — utilise les getters réels générés par Lombok depuis User.java
    // User.java a : getUserId(), getUsername(), getFirstName(), getLastName()
    // Role.java a : getRoleName()
    // Site.java a : getSiteName()
    public static AuditLogDTO from(AuditLog log) {
        AuditLogDTOBuilder b = AuditLogDTO.builder()
                .id          (log.getId())
                .eventType   (log.getEventType() != null ? log.getEventType().name() : null)
                .severity    (log.getSeverity()  != null ? log.getSeverity().name()  : null)
                .username    (log.getUsername())   // snapshot dénormalisé
                .ipAddress   (log.getIpAddress())
                .httpMethod  (log.getHttpMethod())
                .endpoint    (log.getEndpoint())
                .action      (log.getAction())
                .entityType  (log.getEntityType())
                .entityId    (log.getEntityId())
                .oldValue    (log.getOldValue())
                .newValue    (log.getNewValue())
                .statusCode  (log.getStatusCode())
                .errorMessage(log.getErrorMessage())
                .durationMs  (log.getDurationMs())
                .sessionId   (log.getSessionId())
                .createdAt   (log.getCreatedAt());

        // ✅ Enrichir depuis User — getters conformes à votre User.java
        if (log.getUser() != null) {
            User u = log.getUser();
            b.userId     (u.getUserId())                         // Integer UserId
                    .username   (u.getUsername())                       // String Username
                    .userFullName(
                            trim(u.getFirstName()) + " " + trim(u.getLastName())
                    )
                    .userRole   (u.getRole() != null ? u.getRole().getRoleName() : null)
                    .userSite   (u.getSite() != null ? u.getSite().getSiteName() : null);
        }

        return b.build();
    }

    private static String trim(String s) {
        return s != null ? s.trim() : "";
    }
}

