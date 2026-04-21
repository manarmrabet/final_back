package com.example.CWMS.model.cwms;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "MenuItems")
// ✅ MODIFICATION : On remplace @Data par les annotations séparées
// pour éviter le conflit Lombok sur les champs booléens préfixés "is"
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

    // ✅ MODIFICATION : boolean primitif → Boolean objet (wrapper)
    // Cela force Lombok à générer getIsTitle()/setIsTitle()
    // au lieu de isTitle()/setTitle() (convention JavaBean primitif)
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
        // ✅ MODIFICATION : initialisation des flags à false si null à la création
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (this.isTitle == null)  this.isTitle  = false;
        if (this.isLayout == null) this.isLayout = false;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // ✅ MODIFICATION : Suppressions des getters/setters manuels
    // Lombok (@Getter/@Setter) les génère maintenant correctement :
    // - getIsTitle() / setIsTitle(Boolean)
    // - getIsLayout() / setIsLayout(Boolean)
    // grâce au type Boolean (wrapper) et non boolean (primitif)
}