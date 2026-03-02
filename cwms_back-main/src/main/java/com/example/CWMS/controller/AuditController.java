package com.example.CWMS.controller;

import com.example.CWMS.dto.AuditLogDTO;
import com.example.CWMS.model.AuditLog.EventType;
import com.example.CWMS.model.AuditLog.Severity;
import com.example.CWMS.model.User;
import com.example.CWMS.repository.AuditLogRepository;
import com.example.CWMS.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/audit")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AuditController {

    private final AuditLogRepository auditLogRepository;
    private final UserRepository     userRepository;

    // ✅ Recherche multicritères paginée
    @GetMapping
    public Page<AuditLogDTO> search(
            @RequestParam(required = false) String    eventType,
            @RequestParam(required = false) String    severity,
            @RequestParam(required = false) Integer   userId,     // Integer comme UserId
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            Pageable pageable) {

        return auditLogRepository
                .search(
                        eventType != null ? EventType.valueOf(eventType) : null,
                        severity  != null ? Severity.valueOf(severity)   : null,
                        userId, from, to, pageable
                )
                .map(AuditLogDTO::from);
    }

    // ✅ Tous les logs d'un utilisateur
    @GetMapping("/user/{userId}")
    public Page<AuditLogDTO> getByUser(@PathVariable Integer userId, Pageable pageable) {
        // ✅ findById(Integer) — conforme UserRepository extends JpaRepository<User, Integer>
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Utilisateur non trouvé: " + userId));

        return auditLogRepository.findByUser(user, pageable)
                .map(AuditLogDTO::from);
    }

    // ✅ Historique de connexions d'un utilisateur
    @GetMapping("/user/{userId}/connections")
    public List<AuditLogDTO> getConnections(@PathVariable Integer userId) {
        return auditLogRepository
                .findConnectionsByUserId(userId)
                .stream()
                .map(AuditLogDTO::from)
                .toList();
    }
}