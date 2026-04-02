package com.example.CWMS.repository.cwms;

import com.example.CWMS.model.cwms.User;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Integer> {
    Optional<User> findByUsername(String username);
    Optional<User> findByEmail(String email);
    boolean existsByUsername(String username);
    boolean existsByEmail(String email);
    List<User> findByIsActive(Boolean isActive);

    @Query("SELECT u FROM User u WHERE u.role.roleId = :roleId")
    List<User> findByRoleId(Integer roleId);


    @Query("UPDATE User u SET u.failedAttempts = ?2 WHERE u.username = ?1")
    @Modifying
    @Transactional
    void updateFailedAttempts(String username, int failAttempts);

    // ✅ Pour delete normal : on met user_id à NULL dans audit_logs avant de supprimer
    @Modifying
    @Query(value = "UPDATE audit_logs SET user_id = NULL WHERE user_id = :userId", nativeQuery = true)
    void detachAuditLogs(@Param("userId") Integer userId);
}