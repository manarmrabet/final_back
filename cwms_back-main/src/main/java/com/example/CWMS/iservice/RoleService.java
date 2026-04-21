package com.example.CWMS.iservice;

import com.example.CWMS.dto.RoleDTO;
import com.example.CWMS.dto.RoleMenuRequest;
import com.example.CWMS.model.cwms.Role;

import java.util.List;

public interface RoleService {
    List<RoleDTO> getAllRoles();
    RoleDTO getRoleById(Integer id);
    RoleDTO createRole(RoleDTO request);
    RoleDTO updateRole(Integer id, RoleDTO request);
    void deleteRole(Integer id);
    void assignMenusToRole(RoleMenuRequest request);
    List<Integer> getMenuIdsByRole(Integer roleId);
    RoleDTO toDTO(Role role);

}
