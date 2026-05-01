package com.example.CWMS.service;

import com.example.CWMS.model.cwms.User;
import com.example.CWMS.repository.cwms.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LoginAttemptServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private LoginAttemptServiceImpl loginAttemptService;

    private User userActif;
    private User userBloque;

    @BeforeEach
    void setUp() {
        // ✅ FIX — setIsActive prend Boolean (pas int)
        userActif = new User();
        userActif.setUsername("admin");
        userActif.setFailedAttempts(0);
        userActif.setAccountNonLocked(true);
        userActif.setIsActive(true);

        userBloque = new User();
        userBloque.setUsername("userBloque");
        userBloque.setFailedAttempts(3);
        userBloque.setAccountNonLocked(false);
        userBloque.setIsActive(false);
    }

    // ── isBlocked ────────────────────────────────────────────────

    @Test
    void isBlocked_compte_actif_retourne_false() {
        when(userRepository.findByUsername("admin"))
                .thenReturn(Optional.of(userActif));

        assertFalse(loginAttemptService.isBlocked("admin"));
    }

    @Test
    void isBlocked_compte_bloque_retourne_true() {
        when(userRepository.findByUsername("userBloque"))
                .thenReturn(Optional.of(userBloque));

        assertTrue(loginAttemptService.isBlocked("userBloque"));
    }

    @Test
    void isBlocked_utilisateur_inexistant_retourne_false() {
        when(userRepository.findByUsername("inconnu"))
                .thenReturn(Optional.empty());

        assertFalse(loginAttemptService.isBlocked("inconnu"));
    }

    // ── loginFailed ──────────────────────────────────────────────

    @Test
    void loginFailed_incremente_compteur() {
        when(userRepository.findByUsername("admin"))
                .thenReturn(Optional.of(userActif));
        when(userRepository.save(any(User.class)))
                .thenAnswer(i -> i.getArgument(0));

        loginAttemptService.loginFailed("admin");

        verify(userRepository, times(1)).save(any(User.class));
        assertEquals(1, userActif.getFailedAttempts());
    }

    @Test
    void loginFailed_bloque_apres_3_echecs() {
        userActif.setFailedAttempts(2);
        when(userRepository.findByUsername("admin"))
                .thenReturn(Optional.of(userActif));
        when(userRepository.save(any(User.class)))
                .thenAnswer(i -> i.getArgument(0));

        loginAttemptService.loginFailed("admin");

        assertFalse(userActif.getAccountNonLocked());
        verify(userRepository).save(userActif);
    }

    // ── loginSucceeded ───────────────────────────────────────────

    @Test
    void loginSucceeded_remet_compteur_a_zero() {
        userActif.setFailedAttempts(2);
        when(userRepository.findByUsername("admin"))
                .thenReturn(Optional.of(userActif));
        when(userRepository.save(any(User.class)))
                .thenAnswer(i -> i.getArgument(0));

        loginAttemptService.loginSucceeded("admin");

        assertEquals(0, userActif.getFailedAttempts());
        assertTrue(userActif.getAccountNonLocked());
        assertNull(userActif.getLockTime());
    }

    // ── getRemainingAttempts ─────────────────────────────────────

    @Test
    void getRemainingAttempts_retourne_valeur_correcte() {
        userActif.setFailedAttempts(1);
        when(userRepository.findByUsername("admin"))
                .thenReturn(Optional.of(userActif));

        int remaining = loginAttemptService.getRemainingAttempts("admin");

        assertEquals(2, remaining);
    }

    @Test
    void getRemainingAttempts_utilisateur_inexistant_retourne_max() {
        when(userRepository.findByUsername("inconnu"))
                .thenReturn(Optional.empty());

        int remaining = loginAttemptService.getRemainingAttempts("inconnu");

        assertEquals(LoginAttemptServiceImpl.MAX_ATTEMPTS, remaining);
    }
}