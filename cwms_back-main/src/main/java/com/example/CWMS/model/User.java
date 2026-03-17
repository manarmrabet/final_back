package com.example.CWMS.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "Users")
@Data
@ToString(exclude = {"role"})
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "UserId")
    private Integer userId;

    @Column(name = "Username", nullable = false, unique = true, length = 100)
    private String username;

    @Column(name = "Email", nullable = false, unique = true, length = 255)
    private String email;

    @Column(name = "FirstName", length = 100)
    private String firstName;

    @Column(name = "LastName", length = 100)
    private String lastName;


    @JsonIgnore
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "RoleId")
    private Role role;


    @JsonIgnore
    @ManyToOne
    @JoinColumn(name = "IdSite") //
    private Site site;

    @Column(name = "IsActive")
    private Boolean isActive;

    @Column(name = "CreatedAt")
    private LocalDateTime createdAt;

    @Column(name = "UpdatedAt")
    private LocalDateTime updatedAt;

    @Column(name = "PasswordHash", length = 500)
    private String passwordHash;

    @Column(name = "failed_attempts")
    private Integer failedAttempts = 0;

    @Column(name = "account_non_locked")
    private Boolean accountNonLocked = true;

    @Column(name = "lock_time")
    private LocalDateTime lockTime;
    public boolean isAccountLocked() {
        return !this.accountNonLocked;
    }

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (isActive == null) isActive = true;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    @Column(name = "must_change_password", nullable = false, columnDefinition = "boolean default true")
    private boolean mustChangePassword = true;

    @Column(name = "credentials_sent", nullable = false, columnDefinition = "boolean default false")
    private boolean credentialsSent = false;
}