package com.example.CWMS.repository.cwms;

import com.example.CWMS.model.cwms.RoleMenuMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface  RoleMenuMappingRepository extends JpaRepository<RoleMenuMapping, Integer> {

//récupère que les identifiants (IDs) des menus
    @Query("SELECT rmm.menuItem.menuItemId FROM RoleMenuMapping rmm WHERE rmm.role.roleId = :roleId")
    List<Integer> findMenuItemIdsByRoleId(Integer roleId);
//Elle supprime toutes les entrées de la table de mapping pour un rôle spécifique ,lorsqu'un role est deleted on supprime toutes les anciennes associations avant les nouvelles
    @Modifying
    @Query("DELETE FROM RoleMenuMapping rmm WHERE rmm.role.roleId = :roleId")
    void deleteByRoleId(Integer roleId);

}
