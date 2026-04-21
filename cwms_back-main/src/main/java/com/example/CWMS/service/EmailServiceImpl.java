package com.example.CWMS.service;

import com.example.CWMS.exception.EmailValidationException;
import com.example.CWMS.iservice.EmailService;
import com.example.CWMS.iservice.EmailValidationService;
import com.example.CWMS.model.cwms.EmailTemplate;
import com.example.CWMS.model.cwms.User;
import com.example.CWMS.repository.cwms.UserRepository;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;

@Service
public class EmailServiceImpl implements EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailServiceImpl.class);

    // Caractères utilisés pour le mot de passe temporaire.
    // On retire I, O, l, 0, 1 car ils sont visuellement confondus dans les emails.
    private static final String PASSWORD_CHARS =
            "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789";
    private static final int PASSWORD_LENGTH = 12;

    // ✅ CORRECTION 5 : on injecte l'interface, pas la classe concrète
    private final EmailValidationService emailValidationService;

    private final JavaMailSender mailSender;
    private final UserRepository userRepository;
    private final AuditServiceImpl auditService;
    private final BCryptPasswordEncoder passwordEncoder;
    private final EmailTemplate credentialsTemplate;

    @Autowired
    public EmailServiceImpl(
            JavaMailSender mailSender,
            EmailValidationService emailValidationService,   // ← interface
            UserRepository userRepository,
            AuditServiceImpl auditService,
            BCryptPasswordEncoder passwordEncoder,
            EmailTemplate credentialsTemplate) {
        this.mailSender = mailSender;
        this.emailValidationService = emailValidationService;
        this.userRepository = userRepository;
        this.auditService = auditService;
        this.passwordEncoder = passwordEncoder;
        this.credentialsTemplate = credentialsTemplate;
    }

    /**
     * Génère un nouveau mot de passe temporaire, met à jour le hash,
     * envoie l'email et marque credentialsSent = true.
     *
     * Ordre des opérations (important) :
     *  1. Charger l'utilisateur
     *  2. Valider l'email AVANT toute modification en base
     *  3. Générer le mot de passe et construire l'email
     *  4. Sauvegarder en base
     *  5. Envoyer l'email
     *  6. Logger l'audit
     */
    @Transactional
    @Override
    public void sendOrResendCredentials(Integer userId) {

        // ── 1. Charger l'utilisateur ────────────────────────────────────
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé"));

        // ── 2. Valider l'email EN PREMIER ──────────────────────────────
        // ✅ CORRECTION 1 : la validation se fait AVANT de modifier la base.
        // Si l'email est invalide, on lève une exception immédiatement
        // et aucune donnée n'est modifiée.
        if (!emailValidationService.isEmailReachable(user.getEmail())) {
            throw new EmailValidationException(
                    "Adresse email non joignable : " + user.getEmail());
        }

        // ── 3. Générer le mot de passe et préparer l'email ─────────────
        // ✅ CORRECTION 2 : SecureRandom au lieu de UUID.substring()
        // → plus aléatoire, pas de tiret parasite, longueur garantie
        String newTempPassword = generateSecurePassword();

        String body = credentialsTemplate.getBody()
                .replace("{firstName}", StringUtils.defaultIfBlank(user.getFirstName(), "Utilisateur"))
                .replace("{username}", user.getUsername())
                .replace("{password}", newTempPassword);

        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(user.getEmail());
        message.setSubject(credentialsTemplate.getSubject());
        message.setText(body);

        // ── 4. Sauvegarder en base (seulement si validation OK) ─────────
        user.setPasswordHash(passwordEncoder.encode(newTempPassword));
        user.setMustChangePassword(true);
        user.setCredentialsSent(true);
        userRepository.save(user);

        // ── 5. Envoyer l'email ──────────────────────────────────────────
        // Si mailSender.send() échoue ici, @Transactional fera le rollback
        // automatiquement et le mot de passe en base sera annulé.
        mailSender.send(message);

        log.info("Identifiants régénérés et envoyés à {} (userId={})", user.getEmail(), userId);

        // ── 6. Audit ────────────────────────────────────────────────────
        auditService.logAction("CREDENTIALS_SENT", "User", userId.toString(), null, null);
    }

    // ─────────────────────────────────────────────────────────────
    // PRIVÉ
    // ─────────────────────────────────────────────────────────────

    /**
     * Génère un mot de passe temporaire sécurisé avec SecureRandom.
     * Utilise un alphabet sans caractères ambigus (I/l/1, O/0).
     */
    private String generateSecurePassword() {
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder(PASSWORD_LENGTH);
        for (int i = 0; i < PASSWORD_LENGTH; i++) {
            sb.append(PASSWORD_CHARS.charAt(random.nextInt(PASSWORD_CHARS.length())));
        }
        return sb.toString();
    }
}