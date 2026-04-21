package com.example.CWMS.iservice;

import com.example.CWMS.exception.EmailValidationException;

/**
 * Interface pour la gestion des envois d'emails liés aux comptes utilisateurs
 */
public interface EmailService {

    /**
     * Envoie (ou renvoie) les identifiants à un utilisateur.
     * - Génère un nouveau mot de passe temporaire
     * - Met à jour le hash dans la base
     * - Envoie l'email avec le template configuré
     * - Marque l'utilisateur comme "credentials envoyés"
     *
     * @param userId l'identifiant de l'utilisateur
     * @throws EmailValidationException si l'email n'est pas joignable
     * @throws RuntimeException si l'utilisateur n'existe pas ou autre erreur métier
     */
    void sendOrResendCredentials(Integer userId);

    // Optionnel : méthode pour envoi d'autres types d'emails (ex: reset password futur)
    // void sendPasswordResetLink(Integer userId, String resetToken);

    // Si tu veux garder une méthode plus générique pour d'autres usages
    // void sendSimpleEmail(String to, String subject, String body);
}