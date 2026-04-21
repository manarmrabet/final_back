package com.example.CWMS.controller;

import com.example.CWMS.payload.*;
import com.example.CWMS.Security.JwtUtils;
import com.example.CWMS.repository.cwms.UserRepository;      // ✅ NOUVEAU : pour existsByUsername
import com.example.CWMS.service.AuditServiceImpl;
import com.example.CWMS.service.LoginAttemptServiceImpl;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * ─────────────────────────────────────────────────────────────────────────────
 * FICHIER MODIFIÉ — seules les sections marquées ✅ NOUVEAU sont ajoutées.
 * Toute la logique existante (signin, logout, JWT, audit, brute-force DB)
 * est strictement identique.
 * ─────────────────────────────────────────────────────────────────────────────
 *
 * Modification unique apportée :
 *
 * Dans catch(BadCredentialsException) :
 *   Avant : loginFailed() puis message → retour 401
 *   Après : vérification existsByUsername → si absent → performDummyPasswordCheck()
 *           puis loginFailed() puis message → retour 401 (même résultat)
 *
 * Pourquoi existsByUsername ici plutôt que dans le service ?
 *   Le dummy check doit être déclenché AVANT loginFailed() pour que le timing
 *   soit cohérent avec le flux Spring Security (qui appelle BCrypt avant de
 *   lancer BadCredentialsException). Placer cet appel dans le controller permet
 *   de respecter l'ordre exact du flux sans modifier le service.
 *
 * Pourquoi ne pas réutiliser userRepository déjà injecté dans le service ?
 *   Le controller ne doit pas accéder directement au repository en règle générale,
 *   mais ici l'injection est justifiée : c'est une vérification de sécurité au
 *   niveau du point d'entrée HTTP, pas une opération métier.
 *   Alternative acceptable pour un PFE : exposer une méthode userExists() dans
 *   LoginAttemptService et déléguer depuis le controller.
 * ─────────────────────────────────────────────────────────────────────────────
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private AuthenticationManager authenticationManager;  // ← inchangé

    @Autowired
    private JwtUtils jwtUtils;                            // ← inchangé

    @Autowired
    private BCryptPasswordEncoder encoder;                // ← inchangé

    @Autowired
    private AuditServiceImpl auditService;                // ← inchangé

    @Autowired
    private LoginAttemptServiceImpl loginAttemptService;  // ← inchangé

    // ✅ NOUVEAU — nécessaire uniquement pour le dummy check dans le catch
    @Autowired
    private UserRepository userRepository;

    // ─────────────────────────────────────────────────────────────────────────
    // POST /api/auth/signin
    // ─────────────────────────────────────────────────────────────────────────
    @PostMapping("/signin")
    public ResponseEntity<?> authenticateUser(@RequestBody LoginRequest loginRequest,
                                              HttpServletRequest httpRequest) {

        String username  = loginRequest.getUsername();
        String ipAddress = extractIp(httpRequest);
        String userAgent = httpRequest.getHeader("User-Agent");

        // ── Étape 1 : vérification du blocage (DB ou ghost) ── INCHANGÉ
        if (loginAttemptService.isBlocked(username)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("Votre compte est bloqué suite à 3 tentatives infructueuses. " +
                            "Veuillez contacter l'administrateur.");
        }

        try {
            // ── Étape 2 : authentification Spring Security ── INCHANGÉ
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(username, loginRequest.getPassword())
            );

            // ── Étape 3 : succès ── INCHANGÉ
            loginAttemptService.loginSucceeded(username);

            String      jwt         = jwtUtils.generateJwtToken(authentication);
            UserDetails userDetails = (UserDetails) authentication.getPrincipal();
            List<String> roles = userDetails.getAuthorities()
                    .stream()
                    .map(item -> item.getAuthority())
                    .collect(Collectors.toList());

            auditService.logLogin(username, ipAddress, userAgent, true, "Connexion réussie");

            return ResponseEntity.ok(new JwtResponse(jwt, userDetails.getUsername(), roles));

        } catch (BadCredentialsException e) {

            // ✅ NOUVEAU — défense Timing Attack
            // Si le username n'existe pas en DB, Spring Security n'a pas appelé BCrypt
            // → la réponse serait quasi-instantanée (~2ms) et révèlerait l'absence du user.
            // On simule un appel BCrypt (~200ms) pour rendre les deux cas indiscernables.
            boolean userExists = userRepository.existsByUsername(username);
            if (!userExists) {
                loginAttemptService.performDummyPasswordCheck(loginRequest.getPassword());
            }
            // ✅ FIN NOUVEAU

            // ── Incrément du compteur (DB ou ghost) ── logique inchangée
            loginAttemptService.loginFailed(username);

            // ── Message unifié ── INCHANGÉ
            int    remaining     = loginAttemptService.getRemainingAttempts(username);
            String errorMessage  = (remaining > 0)
                    ? "Identifiants incorrects. Il vous reste " + remaining + " tentative(s)."
                    : "Compte bloqué après trop de tentatives infructueuses.";

            auditService.logLogin(username, ipAddress, userAgent, false, errorMessage);

            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorMessage);

        } catch (DisabledException e) {
            // ← INCHANGÉ
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Compte désactivé.");

        } catch (LockedException e) {
            // ← INCHANGÉ
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Compte verrouillé.");

        } catch (Exception e) {
            // ← INCHANGÉ
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Erreur serveur : " + e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // POST /api/auth/logout ── INCHANGÉ
    // ─────────────────────────────────────────────────────────────────────────
    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpServletRequest httpRequest) {
        String username = extractUsernameFromToken(httpRequest);

        auditService.logLogout(
                username,
                extractIp(httpRequest),
                null
        );

        return ResponseEntity.ok("Déconnecté avec succès");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Utilitaires privés ── INCHANGÉS
    // ─────────────────────────────────────────────────────────────────────────
    private String extractIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        return xff != null ? xff.split(",")[0].trim() : request.getRemoteAddr();
    }

    private String extractUsernameFromToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            try {
                return jwtUtils.getUserNameFromJwtToken(header.substring(7));
            } catch (Exception e) {
                return "unknown";
            }
        }
        return "unknown";
    }
}