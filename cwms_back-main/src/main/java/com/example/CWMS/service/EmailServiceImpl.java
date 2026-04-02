package com.example.CWMS.service;

import com.example.CWMS.exception.EmailValidationException;
import com.example.CWMS.iservice.EmailService;
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

import java.util.UUID;

@Service
public class EmailServiceImpl implements EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailServiceImpl.class);

    private final JavaMailSender mailSender;
    private final EmailValidationServiceImpl emailValidationService;
    private final UserRepository userRepository;
    private final AuditServiceImpl auditService;
    private final BCryptPasswordEncoder passwordEncoder;
    private final EmailTemplate credentialsTemplate;

    @Autowired
    public EmailServiceImpl(
            JavaMailSender mailSender,
            EmailValidationServiceImpl emailValidationService,
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
     * Génère un NOUVEAU mot de passe temporaire, met à jour le hash,
     * envoie l'email et marque credentialsSent = true
     */
    @Transactional
    @Override
    public void sendOrResendCredentials(Integer userId) {

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé"));

        // Option : décommente si tu veux interdire le renvoi
        // if (user.isCredentialsSent()) {
        //     throw new IllegalStateException("Les identifiants ont déjà été envoyés.");
        // }

        // Génération nouveau mot de passe temporaire (10 caractères)
        String newTempPassword = UUID.randomUUID().toString().substring(0, 10);

        // Mise à jour des champs
        user.setPasswordHash(passwordEncoder.encode(newTempPassword));
        user.setMustChangePassword(true);
        user.setCredentialsSent(true);

        // Sauvegarde
        userRepository.save(user);

        // Construction du corps de l'email à partir du template
        String body = credentialsTemplate.getBody()
                .replace("{firstName}", StringUtils.defaultIfBlank(user.getFirstName(), "Utilisateur"))
                .replace("{username}", user.getUsername())
                .replace("{password}", newTempPassword);

        // Validation de l'email
        if (!emailValidationService.isEmailReachable(user.getEmail())) {
            throw new EmailValidationException("Adresse email non joignable : " + user.getEmail());
        }

        // Préparation et envoi de l'email
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(user.getEmail());
        message.setSubject(credentialsTemplate.getSubject());
        message.setText(body);

        mailSender.send(message);

        log.info("Identifiants régénérés et envoyés à {} (userId={})", user.getEmail(), userId);

        // Audit (si ton AuditServiceImpl existe et a cette méthode)
        auditService.logAction("CREDENTIALS_SENT", "User", userId.toString(), null, null);
    }
}