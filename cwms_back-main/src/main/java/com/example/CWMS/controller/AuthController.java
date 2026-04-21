package com.example.CWMS.controller;

import com.example.CWMS.payload.*;
import com.example.CWMS.Security.JwtUtils;
import com.example.CWMS.service.AuditServiceImpl;
import com.example.CWMS.service.LoginAttemptServiceImpl; // ✅ Nouveau service
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

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private BCryptPasswordEncoder encoder;

    @Autowired
    private AuditServiceImpl auditService;

    @Autowired
    private LoginAttemptServiceImpl loginAttemptService; // ✅ Injection pour la sécurité brute-force

    @PostMapping("/signin")
    public ResponseEntity<?> authenticateUser(@RequestBody LoginRequest loginRequest,
                                              HttpServletRequest httpRequest) {

        String username = loginRequest.getUsername();
        String ipAddress = extractIp(httpRequest);
        String userAgent = httpRequest.getHeader("User-Agent");

        // 1. VÉRIFICATION PRÉALABLE : Le compte est-il déjà bloqué ?
        if (loginAttemptService.isBlocked(username)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("Votre compte est bloqué suite à 3 tentatives infructueuses. Veuillez contacter l'administrateur.");
        }

        try {
            // 2. TENTATIVE D'AUTHENTIFICATION
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(username, loginRequest.getPassword())
            );

            // 3. SUCCÈS : Réinitialisation du compteur de tentatives
            loginAttemptService.loginSucceeded(username);

            String jwt = jwtUtils.generateJwtToken(authentication);
            UserDetails userDetails = (UserDetails) authentication.getPrincipal();
            List<String> roles = userDetails.getAuthorities()
                    .stream()
                    .map(item -> item.getAuthority())
                    .collect(Collectors.toList());

            // ✅ Log connexion réussie dans l'Audit
            auditService.logLogin(username, ipAddress, userAgent, true, "Connexion réussie");

            return ResponseEntity.ok(new JwtResponse(jwt, userDetails.getUsername(), roles));

        } catch (BadCredentialsException e) {
            // 4. ÉCHEC : Incrémenter le compteur de tentatives
            loginAttemptService.loginFailed(username);

            int remaining = loginAttemptService.getRemainingAttempts(username);
            String errorMessage = (remaining > 0)
                    ? "Identifiants incorrects. Il vous reste " + remaining + " tentative(s)."
                    : "Compte bloqué après trop de tentatives infructueuses.";

            // ✅ Log échec connexion dans l'Audit
            auditService.logLogin(username, ipAddress, userAgent, false, errorMessage);

            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorMessage);

        } catch (DisabledException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Compte désactivé.");
        } catch (LockedException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Compte verrouillé.");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Erreur serveur : " + e.getMessage());
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpServletRequest httpRequest) {
        String username = extractUsernameFromToken(httpRequest);

        // ✅ Log déconnexion
        auditService.logLogout(
                username,
                extractIp(httpRequest),
                null
        );

        return ResponseEntity.ok("Déconnecté avec succès");
    }

    // ── Utilitaires privés ──────────────────────────────────────

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