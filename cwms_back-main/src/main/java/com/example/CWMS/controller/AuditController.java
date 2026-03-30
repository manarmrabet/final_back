package com.example.CWMS.controller;

import com.example.CWMS.dto.ApiResponse;
import com.example.CWMS.dto.AuditLogDTO;
import com.example.CWMS.model.AuditLog.EventType;
import com.example.CWMS.model.AuditLog.Severity;
import com.example.CWMS.model.User;
import com.example.CWMS.repository.AuditLogRepository;
import com.example.CWMS.repository.UserRepository;
import lombok.RequiredArgsConstructor;

// ✅ ON UTILISE LE RESOURCE DE SPRING, PAS CELUI DE JAKARTA
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/audit")
@RequiredArgsConstructor
public class AuditController {

    private final AuditLogRepository auditLogRepository;
    private final UserRepository     userRepository;
    private static final String ARCHIVE_DIR = "archives/audit_logs/";

    @GetMapping
    public ResponseEntity<ApiResponse<Page<AuditLogDTO>>> search(
            @RequestParam(required = false) String    eventType,
            @RequestParam(required = false) String    severity,
            @RequestParam(required = false) Integer   userId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            Pageable pageable) {

        EventType et  = (eventType != null && !eventType.isEmpty())
                ? EventType.valueOf(eventType) : null;
        Severity  sev = (severity  != null && !severity.isEmpty())
                ? Severity.valueOf(severity)   : null;

        return ResponseEntity.ok(ApiResponse.success(
                auditLogRepository.search(et, sev, userId, from, to, pageable)
                        .map(AuditLogDTO::from)
        ));
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<ApiResponse<Page<AuditLogDTO>>> getByUser(
            @PathVariable Integer userId, Pageable pageable) {

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé: " + userId));

        return ResponseEntity.ok(ApiResponse.success(
                auditLogRepository.findByUser(user, pageable).map(AuditLogDTO::from)
        ));
    }

    @GetMapping("/user/{userId}/connections")
    public ResponseEntity<ApiResponse<List<AuditLogDTO>>> getConnections(
            @PathVariable Integer userId) {

        return ResponseEntity.ok(ApiResponse.success(
                auditLogRepository.findConnectionsByUserId(userId)
                        .stream()
                        .map(AuditLogDTO::from)
                        .toList()
        ));
    }

    @GetMapping("/archives")
    public ResponseEntity<ApiResponse<List<String>>> listArchives() {
        File folder = new File(ARCHIVE_DIR);
        if (!folder.exists()) return ResponseEntity.ok(ApiResponse.success(List.of()));

        List<String> files = Arrays.stream(folder.listFiles())
                .filter(f -> f.getName().endsWith(".csv"))
                .map(File::getName)
                .collect(Collectors.toList());

        return ResponseEntity.ok(ApiResponse.success(files));
    }

    @GetMapping("/archives/download/{filename}")
    public ResponseEntity<Resource> downloadArchive(@PathVariable String filename) {
        try {
            Path filePath = Paths.get(ARCHIVE_DIR).resolve(filename).normalize();
            // ✅ Maintenant 'Resource' est bien org.springframework.core.io.Resource
            Resource resource = new UrlResource(filePath.toUri());

            if (resource.exists()) {
                return ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType("text/csv"))
                        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + resource.getFilename() + "\"")
                        .body(resource);
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }
}