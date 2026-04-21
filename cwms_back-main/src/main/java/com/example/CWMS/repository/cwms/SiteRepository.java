package com.example.CWMS.repository.cwms;

import com.example.CWMS.model.cwms.Site;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SiteRepository extends JpaRepository<Site, Integer> {
    Optional<Site> findBySiteNameIgnoreCase(String siteName);
}