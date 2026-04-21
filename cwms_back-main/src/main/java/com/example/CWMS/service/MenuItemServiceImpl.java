package com.example.CWMS.service;

import com.example.CWMS.audit.Auditable;
import com.example.CWMS.dto.MenuItemDTO;
import com.example.CWMS.iservice.MenuItemService;
import com.example.CWMS.model.cwms.MenuItem;
import com.example.CWMS.model.cwms.User;
import com.example.CWMS.model.cwms.Role;
import com.example.CWMS.model.cwms.RoleMenuMapping;
import com.example.CWMS.repository.cwms.MenuItemRepository;
import com.example.CWMS.repository.cwms.RoleMenuMappingRepository;
import com.example.CWMS.repository.cwms.RoleRepository;
import com.example.CWMS.repository.cwms.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MenuItemServiceImpl implements MenuItemService {

    private final MenuItemRepository menuItemRepository;
    private final UserRepository userRepository;
    private final RoleMenuMappingRepository roleMenuMappingRepository;
    private final RoleRepository roleRepository;

    public List<MenuItemDTO> getMenuItemsForCurrentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByUsername(email)
                .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé"));

        return menuItemRepository.findMenuItemsByRoleId(user.getRole().getRoleId())
                .stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public List<MenuItemDTO> getAllMenuItems() {
        return menuItemRepository.findAll().stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    @Auditable(action = "MENU_CREATED", entityType = "MenuItem")
    public MenuItemDTO createMenuItem(MenuItemDTO request) {

        // ✅ MODIFICATION : Résoudre le parent depuis la BDD si un parentId est fourni
        MenuItem parent = null;
        if (request.getParentId() != null) {
            parent = menuItemRepository.findById(request.getParentId())
                    .orElseThrow(() -> new RuntimeException("Parent not found: " + request.getParentId()));
        }

        // ✅ MODIFICATION : isTitle et isLayout sont calculés automatiquement
        // - Si pas de parent → menu principal → isTitle=true, isLayout=true
        // - Si parent présent → sous-menu enfant → isTitle=false, isLayout=false
        boolean isParent = (parent == null);

        MenuItem item = MenuItem.builder()
                .label(request.getLabel())
                .icon(request.getIcon())
                .link(request.getLink())
                .parent(parent)
                .isTitle(isParent)   // ✅ true si parent, false si enfant
                .isLayout(isParent)  // ✅ true si parent, false si enfant
                .build();

        return toDTO(menuItemRepository.save(item));
    }

    @Override
    @Transactional
    @Auditable(action = "MENU_UPDATED", entityType = "MenuItem")
    public MenuItemDTO updateMenuItem(Integer id, MenuItemDTO request) {
        MenuItem item = menuItemRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("MenuItem not found: " + id));

        if (request.getLabel() != null) item.setLabel(request.getLabel());
        if (request.getIcon() != null) item.setIcon(request.getIcon());
        if (request.getLink() != null) item.setLink(request.getLink());

        // ✅ MODIFICATION : Recalcul automatique de isTitle et isLayout
        // selon la présence ou non d'un parentId dans la requête
        if (request.getParentId() != null) {
            MenuItem parent = menuItemRepository.findById(request.getParentId())
                    .orElseThrow(() -> new RuntimeException("Parent not found: " + request.getParentId()));
            item.setParent(parent);
            // ✅ Enfant → isTitle=false, isLayout=false
            item.setIsTitle(false);
            item.setIsLayout(false);
        } else {
            // ✅ Pas de parent → menu principal → isTitle=true, isLayout=true
            item.setParent(null);
            item.setIsTitle(true);
            item.setIsLayout(true);
        }

        return toDTO(menuItemRepository.save(item));
    }

    @Override
    @Transactional
    @Auditable(action = "MENU_DELETED", entityType = "MenuItem")
    public void deleteMenuItem(Integer id) {
        menuItemRepository.deleteById(id);
    }

    @Override
    public MenuItemDTO toDTO(MenuItem item) {
        return MenuItemDTO.builder()
                .menuItemId(item.getMenuItemId())
                .label(item.getLabel())
                .icon(item.getIcon())
                .link(item.getLink())
                .parentId(item.getParent() != null ? item.getParent().getMenuItemId() : null)
                .isTitle(item.getIsTitle())
                .isLayout(item.getIsLayout())
                .createdAt(item.getCreatedAt())
                .updatedAt(item.getUpdatedAt())
                .build();
    }

    @Override
    @Transactional
    @Auditable(action = "MENU_SAVED", entityType = "MenuItem")
    public void saveRoleMenuMappings(Integer roleId, List<Integer> menuItemIds) {
        roleMenuMappingRepository.deleteByRoleId(roleId);

        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new RuntimeException("Rôle non trouvé"));

        List<RoleMenuMapping> mappings = menuItemIds.stream()
                .map(menuId -> {
                    MenuItem menuItem = menuItemRepository.findById(menuId)
                            .orElseThrow(() -> new RuntimeException("Menu " + menuId + " non trouvé"));

                    return RoleMenuMapping.builder()
                            .role(role)
                            .menuItem(menuItem)
                            .build();
                })
                .collect(Collectors.toList());

        roleMenuMappingRepository.saveAll(mappings);
    }

    @Override
    public List<Integer> getMenuItemIdsForRole(Integer roleId) {
        return roleMenuMappingRepository.findMenuItemIdsByRoleId(roleId);
    }
}