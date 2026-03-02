package com.example.CWMS.controller;

import com.example.CWMS.payload.*;
import com.example.CWMS.Security.JwtUtils;
import com.example.CWMS.service.AuditService;  // ✅ import
import jakarta.servlet.http.HttpServletRequest;  // ✅ import
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
    private AuditService auditService;  // ✅ injecter AuditService

    @PostMapping("/signin")
    public ResponseEntity<?> authenticateUser(@RequestBody LoginRequest loginRequest,
                                              HttpServletRequest httpRequest) { // ✅ ajouter HttpServletRequest

        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            loginRequest.getUsername(),
                            loginRequest.getPassword()
                    )
            );

            String jwt = jwtUtils.generateJwtToken(authentication);
            UserDetails userDetails = (UserDetails) authentication.getPrincipal();
            List<String> roles = userDetails.getAuthorities()
                    .stream()
                    .map(item -> item.getAuthority())
                    .collect(Collectors.toList());

            // ✅ Log connexion réussie
            auditService.logLogin(
                    loginRequest.getUsername(),
                    extractIp(httpRequest),
                    httpRequest.getHeader("User-Agent"),
                    true,
                    null
            );

            return ResponseEntity.ok(new JwtResponse(jwt, userDetails.getUsername(), roles));

        } catch (BadCredentialsException e) {

            // ✅ Log échec connexion
            auditService.logLogin(
                    loginRequest.getUsername(),
                    extractIp(httpRequest),
                    httpRequest.getHeader("User-Agent"),
                    false,
                    null
            );

            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body("Username or password incorrect");

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error: " + e.getMessage());
        }
    }

    // ✅ Endpoint logout — Angular appelle ça avant de supprimer le token
    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpServletRequest httpRequest) {

        // Récupérer le username depuis le token JWT
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