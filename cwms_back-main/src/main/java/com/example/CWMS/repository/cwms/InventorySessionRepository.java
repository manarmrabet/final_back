package com.example.CWMS.repository.cwms;

import com.example.CWMS.model.cwms.InventorySession;
import com.example.CWMS.model.cwms.InventorySession.SessionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface InventorySessionRepository extends JpaRepository<InventorySession, Long> {

    List<InventorySession> findAllByOrderByCreatedAtDesc();


}
