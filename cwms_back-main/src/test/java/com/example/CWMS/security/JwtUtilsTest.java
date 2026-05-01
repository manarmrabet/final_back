package com.example.CWMS.security;

import com.example.CWMS.Security.JwtUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class JwtUtilsTest {

    private JwtUtils jwtUtils;

    @BeforeEach
    void setUp() {
        jwtUtils = new JwtUtils();
        // Injecter le secret sans démarrer Spring
        ReflectionTestUtils.setField(jwtUtils, "jwtSecret",
                "cwms-secret-key-very-long-for-hmac-sha256-minimum-32-chars");
    }

    @Test
    void generateJwtToken_retourne_token_non_null() {
        Authentication auth = creerAuthentification("admin", "ROLE_ADMIN");
        String token = jwtUtils.generateJwtToken(auth);
        assertNotNull(token);
        assertFalse(token.isEmpty());
    }

    @Test
    void getUserNameFromJwtToken_retourne_bon_username() {
        Authentication auth = creerAuthentification("admin", "ROLE_ADMIN");
        String token = jwtUtils.generateJwtToken(auth);
        String username = jwtUtils.getUserNameFromJwtToken(token);
        assertEquals("admin", username);
    }

    @Test
    void validateJwtToken_token_valide_retourne_true() {
        Authentication auth = creerAuthentification("admin", "ROLE_ADMIN");
        String token = jwtUtils.generateJwtToken(auth);
        assertTrue(jwtUtils.validateJwtToken(token));
    }

    @Test
    void validateJwtToken_token_invalide_retourne_false() {
        assertFalse(jwtUtils.validateJwtToken("token.invalide.xxx"));
    }

    @Test
    void validateJwtToken_token_vide_retourne_false() {
        assertFalse(jwtUtils.validateJwtToken(""));
    }

    // ── Utilitaire ──────────────────────────────────────────────
    private Authentication creerAuthentification(String username, String role) {
        UserDetails user = new User(username, "password",
                List.of(new SimpleGrantedAuthority(role)));
        return new UsernamePasswordAuthenticationToken(
                user, null, user.getAuthorities());
    }
}