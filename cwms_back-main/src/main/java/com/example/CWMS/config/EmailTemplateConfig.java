package com.example.CWMS.config;

import com.example.CWMS.model.cwms.EmailTemplate;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.io.InputStream;

@Configuration
public class EmailTemplateConfig {

    private static final Logger log = LoggerFactory.getLogger(EmailTemplateConfig.class);

    @Value("classpath:email/credentials-template.json")
    private Resource templateResource;

    @Bean
    public EmailTemplate credentialsTemplate(ObjectMapper objectMapper) {
        try (InputStream inputStream = templateResource.getInputStream()) {
            return objectMapper.readValue(inputStream, EmailTemplate.class);
        } catch (IOException e) {
            log.error("Impossible de charger le template credentials-template.json", e);
            // Option 1 : fallback template hardcodé
            EmailTemplate fallback = new EmailTemplate();
            fallback.setSubject("Vos identifiants CWMS (fallback)");
            fallback.setBody("Bonjour {firstName},\n\n" +
                    "Nom d'utilisateur : {username}\n" +
                    "Mot de passe temporaire : {password}\n\n" +
                    "Cordialement.");
            return fallback;

            // Option 2 : throw exception et arrêter le démarrage
            // throw new IllegalStateException("Template email manquant ou invalide", e);
        }
    }
}