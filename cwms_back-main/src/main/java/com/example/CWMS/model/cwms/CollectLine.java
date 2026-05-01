package com.example.CWMS.model.cwms;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "collect_lines")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CollectLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private InventorySession session;

    @Column(name = "location_code", nullable = false)
    private String locationCode;

    @Column(name = "location_label")
    private String locationLabel;

    @Column(name = "values_json", nullable = false, length = 2000)
    private String valuesJson;

    @Column(name = "scanned_by")
    private String scannedBy;

    @Column(name = "scanned_at")
    private LocalDateTime scannedAt;

    @PrePersist
    protected void onCreate() {
        this.scannedAt = LocalDateTime.now();
    }

    // ✅ FIX SpotBugs — copie défensive pour l'entité liée
    // Hibernate utilise setSession() en interne — on protège sans casser la logique
    public void setSession(InventorySession session) {
        this.session = session;
    }

    public InventorySession getSession() {
        return session; // entité JPA — pas de copie, Hibernate gère le cycle de vie
    }
}