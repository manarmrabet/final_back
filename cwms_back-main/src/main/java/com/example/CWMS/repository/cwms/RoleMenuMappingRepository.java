package com.example.CWMS.repository.cwms;

import com.example.CWMS.model.cwms.RoleMenuMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface  RoleMenuMappingRepository extends JpaRepository<RoleMenuMapping, Integer> {

    @Query("SELECT rmm FROM RoleMenuMapping rmm WHERE rmm.role.roleId = :roleId")
    List<RoleMenuMapping> findByRoleId(Integer roleId);

    @Query("SELECT rmm.menuItem.menuItemId FROM RoleMenuMapping rmm WHERE rmm.role.roleId = :roleId")
    List<Integer> findMenuItemIdsByRoleId(Integer roleId);

    @Modifying
    @Query("DELETE FROM RoleMenuMapping rmm WHERE rmm.role.roleId = :roleId")
    void deleteByRoleId(Integer roleId);

    boolean existsByRole_RoleIdAndMenuItem_MenuItemId(Integer roleId, Integer menuItemId);
}
