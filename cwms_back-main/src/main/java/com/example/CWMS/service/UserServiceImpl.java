package com.example.CWMS.service;

import com.example.CWMS.audit.Auditable;
import com.example.CWMS.dto.UserDTO;
import com.example.CWMS.iservice.UserService;
import com.example.CWMS.model.*;
import com.example.CWMS.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class UserServiceImpl implements UserService {

    @Autowired private UserRepository     userRepository;
    @Autowired private RoleRepository     roleRepository;
    @Autowired private SiteRepository     siteRepository;
    @Autowired private AuditLogRepository auditLogRepository;
    @Autowired private BCryptPasswordEncoder passwordEncoder;
    @Autowired private EmailServiceImpl   emailService;
    @Autowired private AuditServiceImpl   auditService;

    @Override
    public List<UserDTO> getAllUsers() {
        return userRepository.findAll().stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    @Override
    public UserDTO getUserById(Integer id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé"));
        return mapToDTO(user);
    }

    // ─────────────────────────────────────────────────────────────
    // CREATE — avec détection doublon email/username avant tout
    // ─────────────────────────────────────────────────────────────
    @Override
    @Transactional
    public UserDTO createUser(UserDTO userDTO) {

        // ✅ 1. Vérification email doublon — loggé proprement, pas de crash session
        if (userRepository.existsByEmail(userDTO.getEmail())) {
            // On log la tentative dans audit AVANT de lever l'exception
            auditService.logFailedCreation(
                    userDTO.getEmail(),
                    "EMAIL_ALREADY_EXISTS",
                    "Tentative de création avec un email déjà existant : " + userDTO.getEmail()
            );
            throw new RuntimeException("Email déjà utilisé : " + userDTO.getEmail());
        }

        // ✅ 2. Vérification username doublon
        if (userRepository.existsByUsername(userDTO.getUserName())) {
            auditService.logFailedCreation(
                    userDTO.getUserName(),
                    "USERNAME_ALREADY_EXISTS",
                    "Tentative de création avec un nom d'utilisateur déjà existant : " + userDTO.getUserName()
            );
            throw new RuntimeException("Nom d'utilisateur déjà utilisé : " + userDTO.getUserName());
        }

        // ✅ 3. Création
        User user = new User();
        String rawPassword = UUID.randomUUID().toString().substring(0, 10);

        updateUserFields(user, userDTO);
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        user.setFailedAttempts(0);
        user.setAccountNonLocked(true);
        user.setCreatedAt(LocalDateTime.now());
        user.setMustChangePassword(true);       // ← ajouté
        user.setCredentialsSent(false);         // ← ajouté

        User savedUser = userRepository.save(user);

        auditService.logAction("USER_CREATED", "User",
                String.valueOf(savedUser.getUserId()), null, mapToDTO(savedUser));

        return mapToDTO(savedUser);
    }

    // ─────────────────────────────────────────────────────────────
    // UPDATE
    // ─────────────────────────────────────────────────────────────
    @Override
    @Transactional
    public UserDTO updateUser(Integer id, UserDTO userDTO) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé"));

        UserDTO before = mapToDTO(user); // snapshot avant modif
        updateUserFields(user, userDTO);
        user.setUpdatedAt(LocalDateTime.now());

        User updatedUser = userRepository.save(user);

        auditService.logAction("USER_UPDATED", "User",
                String.valueOf(id), before, mapToDTO(updatedUser));

        return mapToDTO(updatedUser);
    }

    // ─────────────────────────────────────────────────────────────
    // DELETE NORMAL — conserve les audit logs (user_id → NULL)
    // ─────────────────────────────────────────────────────────────
    @Override
    @Transactional
    public void deleteUser(Integer id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé"));

        // 1. Détacher les audit logs (user_id = NULL) pour garder la traçabilité
        userRepository.detachAuditLogs(id);

        // 2. Supprimer le user
        userRepository.deleteById(id);

        // 3. Log audit avec username en snapshot (user déjà supprimé, on utilise le username)
        auditService.logActionWithUsername(
                "USER_DELETED", "User", String.valueOf(id),
                user.getUsername(), mapToDTO(user), null
        );
    }

    // ─────────────────────────────────────────────────────────────
    // DELETE FORCÉ — supprime user ET toute sa traçabilité
    // ─────────────────────────────────────────────────────────────
    @Override
    @Transactional
    public void forceDeleteUser(Integer id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé"));

        String username = user.getUsername();

        // 1. Supprimer tous les audit logs liés
        auditLogRepository.deleteAllByUserId(id);

        // 2. Supprimer le user
        userRepository.deleteById(id);

        // 3. Log de l'action de suppression forcée (sans référence au user supprimé)
        auditService.logActionWithUsername(
                "USER_FORCE_DELETED", "User", String.valueOf(id),
                username, null, null
        );
    }

    // ─────────────────────────────────────────────────────────────
    // PRIVÉ
    // ─────────────────────────────────────────────────────────────
    private void updateUserFields(User user, UserDTO dto) {
        if (dto.getUserName()  != null) user.setUsername(dto.getUserName());
        if (dto.getEmail()     != null) user.setEmail(dto.getEmail());
        if (dto.getFirstName() != null) user.setFirstName(dto.getFirstName());
        if (dto.getLastName()  != null) user.setLastName(dto.getLastName());

        if (dto.getIsActive() != null) {
            boolean requestedActive = (dto.getIsActive() == 1);
            if (requestedActive) {
                user.setIsActive(true);
                user.setAccountNonLocked(true);
                user.setFailedAttempts(0);
                user.setLockTime(null);
            } else {
                user.setIsActive(false);
            }
        }

        if (dto.getRoleName() != null && !dto.getRoleName().isEmpty()) {
            roleRepository.findAll().stream()
                    .filter(r -> r.getRoleName().equalsIgnoreCase(dto.getRoleName()))
                    .findFirst().ifPresent(user::setRole);
        }

        if (dto.getSiteName() != null && !dto.getSiteName().isEmpty()) {
            siteRepository.findAll().stream()
                    .filter(s -> s.getSiteName().equalsIgnoreCase(dto.getSiteName()))
                    .findFirst().ifPresent(user::setSite);
        }
    }

    @Override
    public UserDTO mapToDTO(User user) {
        UserDTO dto = new UserDTO();
        dto.setId(user.getUserId());
        dto.setUserName(user.getUsername());
        dto.setEmail(user.getEmail());
        dto.setFirstName(user.getFirstName());
        dto.setLastName(user.getLastName());

        boolean isReallyActive = (user.getIsActive() != null && user.getIsActive())
                && (user.getAccountNonLocked() != null && user.getAccountNonLocked());
        dto.setIsActive(isReallyActive ? 1 : 0);

        if (user.getRole() != null) dto.setRoleName(user.getRole().getRoleName());
        if (user.getSite() != null) dto.setSiteName(user.getSite().getSiteName());

        dto.setMustChangePassword(user.isMustChangePassword());
        dto.setCredentialsSent(user.isCredentialsSent());

        return dto;
    }
}