package com.example.CWMS.model.cwms;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "RoleMenuMappings")
@Data
@ToString(exclude = {"role", "menuItem"})
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoleMenuMapping {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "RoleMenuMappingId")
    private Integer roleMenuMappingId;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "RoleId", nullable = false)
    private Role role;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "MenuItemId", nullable = false)
    private MenuItem menuItem;

    @Column(name = "CreatedAt")
    private LocalDateTime createdAt;

    @Column(name = "UpdatedAt")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // ✅ FIX SpotBugs — entités JPA liées par @ManyToOne
    // Hibernate gère le cycle de vie — accesseurs explicites sans copie
    public void setRole(Role role) { this.role = role; }
    public Role getRole()          { return role; }

    public void setMenuItem(MenuItem menuItem) { this.menuItem = menuItem; }
    public MenuItem getMenuItem()              { return menuItem; }
}