package com.example.CWMS.service;

import com.example.CWMS.dto.*;
import com.example.CWMS.iservice.RoleService;
import com.example.CWMS.model.cwms.MenuItem;
import com.example.CWMS.model.cwms.Role;
import com.example.CWMS.model.cwms.RoleMenuMapping;
import com.example.CWMS.repository.cwms.MenuItemRepository;
import com.example.CWMS.repository.cwms.RoleMenuMappingRepository;
import com.example.CWMS.repository.cwms.RoleRepository;
import com.example.CWMS.repository.cwms.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RoleServiceImpl implements RoleService {

    private final RoleRepository roleRepository;
    private final RoleMenuMappingRepository roleMenuMappingRepository;
    private final MenuItemRepository menuItemRepository;
    private final UserRepository userRepository;

    public List<RoleDTO> getAllRoles() {
        return roleRepository.findAll().stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public RoleDTO getRoleById(Integer id) {
        Role role = roleRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Role not found: " + id));
        return toDTO(role);
    }

    @Transactional
    public RoleDTO createRole(RoleDTO request) {
        Role role = Role.builder()
                .roleName(request.getRoleName())
                .description(request.getDescription())
                .build();
        return toDTO(roleRepository.save(role));
    }

    @Transactional
    public RoleDTO updateRole(Integer id, RoleDTO request) {
        Role role = roleRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Role not found: " + id));
        if (request.getRoleName() != null) role.setRoleName(request.getRoleName());
        if (request.getDescription() != null) role.setDescription(request.getDescription());
        return toDTO(roleRepository.save(role));
    }

    @Transactional
    public void deleteRole(Integer id) {
        roleRepository.deleteById(id);
    }

    @Transactional
    public void assignMenusToRole(RoleMenuRequest request) {
        Role role = roleRepository.findById(request.getRoleId())
                .orElseThrow(() -> new RuntimeException("Role not found: " + request.getRoleId()));

        // Delete existing mappings
        roleMenuMappingRepository.deleteByRoleId(request.getRoleId());

        // Create new mappings
        for (Integer menuItemId : request.getMenuItemIds()) {
            MenuItem menuItem = menuItemRepository.findById(menuItemId)
                    .orElseThrow(() -> new RuntimeException("MenuItem not found: " + menuItemId));

            RoleMenuMapping mapping = RoleMenuMapping.builder()
                    .role(role)
                    .menuItem(menuItem)
                    .build();
            roleMenuMappingRepository.save(mapping);
        }
    }

    public List<Integer> getMenuIdsByRole(Integer roleId) {
        return roleMenuMappingRepository.findMenuItemIdsByRoleId(roleId);
    }

    public RoleDTO toDTO(Role role) {
        List<Integer> menuIds = roleMenuMappingRepository.findMenuItemIdsByRoleId(role.getRoleId());
        long userCount = userRepository.findByRoleId(role.getRoleId()).size();

        return RoleDTO.builder()
                .roleId(role.getRoleId())
                .roleName(role.getRoleName())
                .description(role.getDescription())
                .createdAt(role.getCreatedAt())
                .updatedAt(role.getUpdatedAt())
                .menuItemIds(menuIds)
                .userCount((int) userCount)
                .build();
    }
}
