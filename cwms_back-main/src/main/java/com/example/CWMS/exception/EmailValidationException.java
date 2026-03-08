package com.example.CWMS.exception;

/**
 * Exception levée quand une adresse email ne peut pas recevoir de messages.
 * Étant une RuntimeException, elle déclenchera automatiquement
 * le rollback de la transaction @Transactional.
 */
public class EmailValidationException extends RuntimeException {
    public EmailValidationException(String message) {
        super(message);
    }
}