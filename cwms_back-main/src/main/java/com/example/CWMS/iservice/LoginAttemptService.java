package com.example.CWMS.iservice;

public interface LoginAttemptService {
    void loginSucceeded(String username);
    void loginFailed(String username);
    boolean isBlocked(String username);
    int getRemainingAttempts(String username);
}
