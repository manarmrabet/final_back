package com.example.CWMS.service;

import com.example.CWMS.iservice.LoginAttemptService;
import com.example.CWMS.repository.cwms.UserRepository;
import com.example.CWMS.security.GhostAttemptStore;          // ✅ NOUVEAU : import du store fantôme
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;

/**
 * ─────────────────────────────────────────────────────────────────────────────
 * FICHIER MODIFIÉ — seules les sections marquées ✅ NOUVEAU sont ajoutées.
 * Toute la logique existante (DB, failedAttempts, isActive, lock) est intacte.
 * ─────────────────────────────────────────────────────────────────────────────
 *
 * Modifications apportées :
 *
 * 1. loginSucceeded() — ajout d'un ghostStore.reset() en fin de méthode.
 *    Pourquoi : si un username était ciblé comme "fantôme" avant d'être créé
 *    en DB, son compteur Redis doit être nettoyé à la première connexion réussie.
 *    → Impact sur l'existant : AUCUN. La logique DB (reset failedAttempts,
 *      setAccountNonLocked, setLockTime) est strictement identique.
 *
 * 2. loginFailed() — ajout d'une branche "user inexistant" avec ghostStore.increment().
 *    Pourquoi : sans cette branche, un username inexistant ne génère aucun compteur,
 *    ce qui différencie son comportement d'un vrai compte (User Enumeration Attack).
 *    → Impact sur l'existant : AUCUN. La branche DB (ifPresent) est strictement
 *      identique. La branche ghost ne s'exécute que si ifPresent ne trouve rien.
 *
 * 3. isBlocked() — ajout d'une vérification ghostStore.isGhostBlocked() en OR.
 *    Pourquoi : un username inexistant bloqué doit retourner true ici, exactement
 *    comme un vrai compte bloqué.
 *    → Impact sur l'existant : AUCUN. La logique DB (orElse(false)) est identique.
 *      La valeur OR ne peut être true que si le ghost est bloqué.
 *
 * 4. getRemainingAttempts() — ajout d'un orElseGet() renvoyant le compteur ghost.
 *    Pourquoi : pour un username inexistant, orElse(MAX_ATTEMPTS) retournait toujours
 *    3 (comme si le compte était neuf), masquant le vrai nombre de tentatives ghost.
 *    → Impact sur l'existant : AUCUN. La logique .map() pour les vrais users est
 *      strictement identique. orElseGet() ne s'exécute que si findByUsername est vide.
 *
 * 5. performDummyPasswordCheck() — NOUVELLE méthode publique.
 *    Pourquoi : BCrypt.matches() prend ~200ms. Sans ce dummy check, la réponse
 *    pour un username inexistant est quasi-instantanée, révélant son absence
 *    (Timing Attack). Cette méthode est appelée depuis AuthController.
 *    → Impact sur l'existant : AUCUN. Méthode additionnelle, pas de surcharge.
 * ─────────────────────────────────────────────────────────────────────────────
 */
@Service
@Transactional
public class LoginAttemptServiceImpl implements LoginAttemptService {

    public static final int MAX_ATTEMPTS = 3;

    /**
     * Hash BCrypt d'un mot de passe quelconque, pré-calculé une fois.
     * Utilisé uniquement pour simuler un appel BCrypt.matches() côté timing.
     * Ce hash ne correspond à aucun compte réel — il est juste là pour que
     * l'appel prenne ~200ms, identique à une vraie vérification de mot de passe.
     */
    // ✅ NOUVEAU — constante pour la défense timing
    private static final String DUMMY_HASH =
            "$2a$10$7EqJtq98hPqEX7fNZaFWoOe3a1.OhTQHImQbLAkLqfF67AaKxNjMK";

    @Autowired
    private UserRepository userRepository;              // ← inchangé

    // ✅ NOUVEAU — injection du store fantôme Redis
    @Autowired
    private BCryptPasswordEncoder encoder;

    @Autowired
    private GhostAttemptStore ghostStore;

    // ─────────────────────────────────────────────────────────────────────────
    // loginSucceeded — logique DB strictement identique
    // Ajout : ghostStore.reset() pour nettoyer un éventuel compteur fantôme
    // ─────────────────────────────────────────────────────────────────────────
    @Override
    public void loginSucceeded(String username) {
        // ← INCHANGÉ : reset complet en DB
        userRepository.findByUsername(username).ifPresent(user -> {
            user.setFailedAttempts(0);
            user.setAccountNonLocked(true);
            user.setLockTime(null);
            userRepository.save(user);
        });

        // ✅ NOUVEAU : nettoyage du compteur Redis fantôme si existant
        ghostStore.reset(username);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // loginFailed — logique DB strictement identique
    // Ajout : branche ghost si le user n'existe pas en DB
    // ─────────────────────────────────────────────────────────────────────────
    @Override
    public void loginFailed(String username) {
        // ← INCHANGÉ : incrémentation et blocage en DB si user trouvé
        boolean userExistsInDb = userRepository.findByUsername(username).map(user -> {
            int newAttempts = (user.getFailedAttempts() != null
                    ? user.getFailedAttempts() : 0) + 1;
            user.setFailedAttempts(newAttempts);

            if (newAttempts >= MAX_ATTEMPTS) {
                user.setAccountNonLocked(false);
                user.setLockTime(LocalDateTime.now());
                user.setIsActive(false); // force intervention admin
            }
            userRepository.save(user);
            return true;
        }).orElse(false);

        // ✅ NOUVEAU : si le user n'existe pas en DB → compteur ghost Redis
        if (!userExistsInDb) {
            ghostStore.increment(username);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // isBlocked — logique DB strictement identique
    // Ajout : vérification ghost en OR
    // ─────────────────────────────────────────────────────────────────────────
    @Override
    public boolean isBlocked(String username) {
        // ← INCHANGÉ : vrai compte bloqué si lock ou inactif en DB
        boolean realBlocked = userRepository.findByUsername(username)
                .map(user -> !user.getAccountNonLocked() || !user.getIsActive())
                .orElse(false);

        // ✅ NOUVEAU : username inexistant bloqué si compteur ghost >= MAX
        boolean ghostBlocked = ghostStore.isGhostBlocked(username);

        return realBlocked || ghostBlocked;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // getRemainingAttempts — logique DB strictement identique
    // Changement : orElse(MAX_ATTEMPTS) → orElseGet(ghost) pour refléter
    // le vrai compteur fantôme au lieu de toujours retourner 3
    // ─────────────────────────────────────────────────────────────────────────
    @Override
    public int getRemainingAttempts(String username) {
        return userRepository.findByUsername(username)
                // ← INCHANGÉ : calcul depuis la DB pour les vrais users
                .map(user -> Math.max(0,
                        MAX_ATTEMPTS - (user.getFailedAttempts() != null
                                ? user.getFailedAttempts() : 0)))
                // ✅ MODIFIÉ : orElse(MAX_ATTEMPTS) → orElseGet(ghost)
                // Avant : retournait toujours 3 si user inexistant (faux)
                // Après : retourne le vrai compteur ghost (0, 1 ou 2)
                .orElseGet(() -> ghostStore.getRemainingGhostAttempts(username));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ✅ NOUVEAU — défense contre le Timing Attack
    // ─────────────────────────────────────────────────────────────────────────
    /**
     * Simule un appel BCrypt.matches() pour égaliser le temps de réponse
     * entre un username existant et un username inexistant.
     *
     * Sans cette méthode :
     *   - User existant   : Spring Security appelle BCrypt → ~200ms
     *   - User inexistant : aucun appel BCrypt  → ~2ms (réponse quasi-instantanée)
     * Un attaquant peut mesurer ce delta et en déduire si le username existe.
     *
     * Avec cette méthode :
     *   Les deux cas prennent ~200ms → delta indiscernable.
     *
     * Le résultat de encoder.matches() est volontairement ignoré.
     * Appelée depuis AuthController dans le catch(BadCredentialsException).
     */
    public void performDummyPasswordCheck(String rawPassword) {
        encoder.matches(rawPassword, DUMMY_HASH); // résultat délibérément ignoré
    }
}