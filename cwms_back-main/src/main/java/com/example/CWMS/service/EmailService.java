package com.example.CWMS.service;

import com.example.CWMS.exception.EmailValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    @Autowired
    private JavaMailSender mailSender;

    @Autowired
    private EmailValidationService emailValidationService;

    /**
     * Valide l'email AVANT d'envoyer quoi que ce soit.
     * Lève une EmailValidationException si l'adresse est injoignable.
     * Cette exception RuntimeException déclenchera le rollback @Transactional.
     */
    public void sendCredentials(String to, String username, String password) {

        // ✅ VALIDATION — Si false, on lance l'exception → rollback automatique
        if (!emailValidationService.isEmailReachable(to)) {
            log.warn("Adresse email rejetée par le serveur MX/SMTP : {}", to);
            throw new EmailValidationException(
                    "L'adresse email '" + to + "' est introuvable ou ne peut pas recevoir de messages."
            );
        }

        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(to);
        message.setSubject("Vos identifiants CWMS");
        message.setText(
                "Bonjour,\n\nVotre compte a été créé.\n" +
                        "Nom d'utilisateur : " + username + "\n" +
                        "Mot de passe temporaire : " + password + "\n\n" +
                        "Cordialement,\nL'administration."
        );
        mailSender.send(message);
        log.info("Identifiants envoyés à {}", to);
    }
}