package com.example.CWMS.model.cwms;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Entity
@Table(name = "Sites")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Site {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "SiteId")
    private int siteId;

    @Column(name = "SiteName", nullable = false, length = 100)
    private String siteName;

    @Column(name = "CreatedAt")
    private Date CreatedAt;

    @Column(name = "UpdatedAt")
    private Date UpdatedAt;
}