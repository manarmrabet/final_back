package com.example.CWMS.model.cwms;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

@Entity
@Table(name = "MenuItems")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = {"roleMappings", "parent"})
public class MenuItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "MenuItemId")
    private Integer menuItemId;

    @Column(name = "Label", nullable = false, length = 255)
    private String label;

    @Column(name = "Icon", nullable = false, length = 255)
    private String icon;

    @Column(name = "Link", nullable = false, length = 255)
    private String link;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ParentId")
    private MenuItem parent;

    @Column(name = "IsTitle")
    private Boolean isTitle;

    @Column(name = "IsLayout")
    private Boolean isLayout;

    @Column(name = "CreatedAt")
    private LocalDateTime createdAt;

    @Column(name = "UpdatedAt")
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "menuItem", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<RoleMenuMapping> roleMappings;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (this.isTitle == null)  this.isTitle  = false;
        if (this.isLayout == null) this.isLayout = false;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // ✅ FIX SpotBugs — getter protégé pour @OneToMany
    // Hibernate gère sa propre collection en interne — unmodifiableList()
    // empêche uniquement la modification externe accidentelle
    public List<RoleMenuMapping> getRoleMappings() {
        return roleMappings == null ? null : Collections.unmodifiableList(roleMappings);
    }
}