package com.example.CWMS.repository.cwms;

import com.example.CWMS.model.cwms.MenuItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface MenuItemRepository extends JpaRepository<MenuItem, Integer> {
    // Jointure entre MenuItem et RoleMenuMapping pour filtrer par RoleId
    @Query("SELECT m FROM MenuItem m JOIN RoleMenuMapping rmm ON m.menuItemId = rmm.menuItem.menuItemId WHERE rmm.role.roleId = :roleId")
    List<MenuItem> findMenuItemsByRoleId(@Param("roleId") Integer roleId);
}