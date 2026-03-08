package com.example.CWMS.service;

import com.example.CWMS.dto.UserDTO;
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

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private BCryptPasswordEncoder passwordEncoder;

    @Autowired
    private EmailService emailService;

    @Autowired
    private SiteRepository siteRepository;

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

    @Override
    @Transactional
    public UserDTO createUser(UserDTO userDTO) {
        User user = new User();

        String rawPassword = UUID.randomUUID().toString().substring(0, 8);
        updateUserFields(user, userDTO);
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        user.setFailedAttempts(0);
        user.setAccountNonLocked(true);
        user.setCreatedAt(LocalDateTime.now());

        if (user.getIsActive() == null) {
            user.setIsActive(true);
        }

        // ⚠️ CHANGEMENT CLÉ : on valide l'email AVANT le save()
        // Si isEmailReachable() retourne false → EmailValidationException levée
        // → @Transactional fait le rollback → rien n'est enregistré en BDD
        emailService.sendCredentials(userDTO.getEmail(), userDTO.getUserName(), rawPassword);

        // Cette ligne n'est atteinte QUE si l'email est valide
        User savedUser = userRepository.save(user);

        return mapToDTO(savedUser);
    }

    @Override
    @Transactional
    public UserDTO updateUser(Integer id, UserDTO userDTO) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé"));

        updateUserFields(user, userDTO);
        user.setUpdatedAt(LocalDateTime.now());

        User updatedUser = userRepository.save(user);
        return mapToDTO(updatedUser);
    }

    @Override
    @Transactional
    public void deleteUser(Integer id) {
        userRepository.deleteById(id);
    }

    private void updateUserFields(User user, UserDTO dto) {
        if (dto.getUserName() != null) user.setUsername(dto.getUserName());
        if (dto.getEmail() != null) user.setEmail(dto.getEmail());
        if (dto.getFirstName() != null) user.setFirstName(dto.getFirstName());
        if (dto.getLastName() != null) user.setLastName(dto.getLastName());

        // --- LOGIQUE DE DÉBLOCAGE ET ACTIVATION ---
        if (dto.getIsActive() != null) {
            boolean requestedActive = (dto.getIsActive() == 1);

            // Si l'admin active le compte, on réinitialise TOUS les verrous de sécurité
            if (requestedActive) {
                user.setIsActive(true);
                user.setAccountNonLocked(true); // Déverrouillage forcé
                user.setFailedAttempts(0);      // Remise à zéro des essais
                user.setLockTime(null);         // Suppression du timestamp de blocage
            } else {
                user.setIsActive(false);
            }
        }

        // 1. GESTION DU RÔLE
        if (dto.getRoleName() != null && !dto.getRoleName().isEmpty()) {
            roleRepository.findAll().stream()
                    .filter(r -> r.getRoleName().equalsIgnoreCase(dto.getRoleName()))
                    .findFirst()
                    .ifPresent(user::setRole);
        }

        // 2. GESTION DU SITE
        if (dto.getSiteName() != null && !dto.getSiteName().isEmpty()) {
            siteRepository.findAll().stream()
                    .filter(s -> s.getSiteName().equalsIgnoreCase(dto.getSiteName()))
                    .findFirst()
                    .ifPresent(user::setSite);
        }
    }

    private UserDTO mapToDTO(User user) {
        UserDTO dto = new UserDTO();
        dto.setId(user.getUserId());
        dto.setUserName(user.getUsername());
        dto.setEmail(user.getEmail());
        dto.setFirstName(user.getFirstName());
        dto.setLastName(user.getLastName());

        // On renvoie 1 si le compte est actif ET non verrouillé par le système
        boolean isReallyActive = (user.getIsActive() != null && user.getIsActive())
                && (user.getAccountNonLocked() != null && user.getAccountNonLocked());

        dto.setIsActive(isReallyActive ? 1 : 0);

        if (user.getRole() != null) {
            dto.setRoleName(user.getRole().getRoleName());
        }
        if (user.getSite() != null) {
            dto.setSiteName(user.getSite().getSiteName());
        }
        return dto;
    }
}