package com.example.CWMS.repository.cwms;

import com.example.CWMS.model.cwms.CollectTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface CollectTemplateRepository extends JpaRepository<CollectTemplate, Long> {
    List<CollectTemplate> findByActiveTrue();
    // FIX : trouver par nom pour éviter les doublons
    Optional<CollectTemplate> findByName(String name);
}