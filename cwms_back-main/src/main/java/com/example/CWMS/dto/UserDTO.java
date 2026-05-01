package com.example.CWMS.dto;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import lombok.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder


public class UserDTO {
    private Integer id;
    private String userName;
    private String email;
    private String firstName;
    private String lastName;
    private Integer isActive;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String roleName;
    private String siteName;
    private List<String> authorities;
    private boolean mustChangePassword;
    private boolean credentialsSent;

    // ✅ FIX SpotBugs — copie défensive setter
    public void setAuthorities(List<String> authorities) {
        this.authorities = authorities == null ? null : new ArrayList<>(authorities);
    }

    // ✅ FIX SpotBugs — protection getter
    public List<String> getAuthorities() {
        return authorities == null ? null : Collections.unmodifiableList(authorities);
    }
}