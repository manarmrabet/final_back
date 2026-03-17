package com.example.CWMS.dto;

import lombok.*;
import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserDTO {

    private Integer       id;
    private String        userName;
    private String        email;
    private String        passwordHash;
    private String        firstName;
    private String        lastName;
    private Integer       isActive;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String        roleName;
    private String        siteName;
    private List<String>  authorities;
    private boolean mustChangePassword;
    private boolean credentialsSent;
}