package com.example.CWMS.controller;

import com.example.CWMS.dto.ApiResponse; // Import de ton DTO
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
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/user/audit") // ✅ Harmonisé (retrait du /user si tu veux rester cohérent avec tes tests)
@RequiredArgsConstructor
public class AuditController {

    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;

    // 1. Recherche multicritères avec pagination
    @GetMapping
    public ResponseEntity<ApiResponse<Page<AuditLogDTO>>> search(
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) String severity,
            @RequestParam(required = false) Integer userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            Pageable pageable) {

        EventType et = (eventType != null && !eventType.trim().isEmpty()) ? EventType.valueOf(eventType) : null;
        Severity sev = (severity != null && !severity.trim().isEmpty()) ? Severity.valueOf(severity) : null;

        Page<AuditLogDTO> result = auditLogRepository.search(et, sev, userId, from, to, pageable)
                .map(AuditLogDTO::from);

        return ResponseEntity.ok(ApiResponse.success(result));
    }

    // 2. Logs d'un utilisateur spécifique (paginé)
    @GetMapping("/user/{userId}")
    public ResponseEntity<ApiResponse<Page<AuditLogDTO>>> getByUser(
            @PathVariable Integer userId,
            Pageable pageable) {

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé: " + userId));

        Page<AuditLogDTO> result = auditLogRepository.findByUser(user, pageable)
                .map(AuditLogDTO::from);

        return ResponseEntity.ok(ApiResponse.success(result));
    }

    // 3. Connexions d'un utilisateur (Liste simple)
    @GetMapping("/user/{userId}/connections")
    public ResponseEntity<ApiResponse<List<AuditLogDTO>>> getConnections(@PathVariable Integer userId) {
        List<AuditLogDTO> connections = auditLogRepository
                .findConnectionsByUserId(userId)
                .stream()
                .map(AuditLogDTO::from)
                .toList();

        return ResponseEntity.ok(ApiResponse.success(connections));
    }
}