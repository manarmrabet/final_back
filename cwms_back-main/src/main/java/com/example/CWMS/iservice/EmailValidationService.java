package com.example.CWMS.iservice;

/**
 * Contrat métier pour la validation d'adresses email.
 * Seule la méthode publiquement utile est exposée ici.
 * Les détails techniques (DNS, SMTP) restent dans l'implémentation.
 */
public interface EmailValidationService {

    /**
     * Vérifie si une adresse email est joignable.
     * Effectue une vérification MX (DNS) puis un handshake SMTP.
     *
     * @param email l'adresse à vérifier
     * @return true si l'adresse semble valide et joignable, false sinon
     */
    boolean isEmailReachable(String email);
}