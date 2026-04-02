package com.example.CWMS.model.cwms;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "MenuItems")
@Data
@ToString(exclude = {"roleMappings", "parent"})
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MenuItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "MenuItemId")
    private Integer menuItemId;
    @Column(name = "Label" , nullable = false, length = 255)
    private String label;
    @Column(name = "Icon", nullable = false, length = 255)
    private String icon;
    @Column(name = "Link", nullable = false, length = 255)
    private String link;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ParentId")
    private MenuItem parent;
    @Column(name = "IsTitle")
    private boolean isTitle;
    @Column(name = "IsLayout")
    private boolean isLayout;

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
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }


    // Getter pour IsTitle (Retourne Boolean pour gérer la nullité si besoin)
    public Boolean getIsTitle() {
        return this.isTitle;
    }

    // Setter pour IsTitle
    public void setIsTitle(Boolean isTitle) {
        this.isTitle = isTitle != null ? isTitle : false;
    }

    // Getter pour IsLayout
    public Boolean getIsLayout() {
        return this.isLayout;
    }

    // Setter pour IsLayout
    public void setIsLayout(Boolean isLayout) {
        this.isLayout = isLayout != null ? isLayout : false;
    }
}