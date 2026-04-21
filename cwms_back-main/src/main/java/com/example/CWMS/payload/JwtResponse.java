package com.example.CWMS.payload;
import java.util.List;

public class JwtResponse {
    private String token;
    private String username;
    private List<String> roles;

    public JwtResponse(String token, String username, List<String> roles) {
        this.token = token;
        this.username = username;
        this.roles = roles;
    }
    // Getters
    public String getToken() { return token; }
    public String getUsername() { return username; }
    public List<String> getRoles() { return roles; }
}