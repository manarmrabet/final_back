package com.example.CWMS.service;

import com.example.CWMS.iservice.LoginAttemptService;
import com.example.CWMS.repository.cwms.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;

@Service
@Transactional
public class LoginAttemptServiceImpl implements LoginAttemptService {
    public static final int MAX_ATTEMPTS = 3;

    @Autowired
    private UserRepository userRepository;

    public void loginSucceeded(String username) {
        userRepository.findByUsername(username).ifPresent(user -> {
            user.setFailedAttempts(0);
            user.setAccountNonLocked(true);
            user.setLockTime(null);
            userRepository.save(user);
        });
    }

    public void loginFailed(String username) {
        userRepository.findByUsername(username).ifPresent(user -> {
            int newAttempts = (user.getFailedAttempts() != null ? user.getFailedAttempts() : 0) + 1;
            user.setFailedAttempts(newAttempts);

            if (newAttempts >= MAX_ATTEMPTS) {
                user.setAccountNonLocked(false);
                user.setLockTime(LocalDateTime.now());
                user.setIsActive(false); // On désactive pour forcer l'intervention admin
            }
            userRepository.save(user);
        });
    }

    public boolean isBlocked(String username) {
        return userRepository.findByUsername(username)
                .map(user -> !user.getAccountNonLocked() || !user.getIsActive())
                .orElse(false);
    }

    public int getRemainingAttempts(String username) {
        return userRepository.findByUsername(username)
                .map(user -> Math.max(0, MAX_ATTEMPTS - (user.getFailedAttempts() != null ? user.getFailedAttempts() : 0)))
                .orElse(MAX_ATTEMPTS);
    }
}