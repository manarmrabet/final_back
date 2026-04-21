package com.example.CWMS.controller;

import com.example.CWMS.dto.*;
import com.example.CWMS.service.MenuItemServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/menu-items")
@RequiredArgsConstructor
public class MenuItemController {

    private final MenuItemServiceImpl menuItemService;

    // Route dynamique pour charger le menu selon le rôle de l'utilisateur connecté
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<List<MenuItemDTO>>> getMyMenuItems() {
        return ResponseEntity.ok(ApiResponse.success(menuItemService.getMenuItemsForCurrentUser()));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<MenuItemDTO>>> getAllMenuItems() {
        return ResponseEntity.ok(ApiResponse.success(menuItemService.getAllMenuItems()));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<MenuItemDTO>> createMenuItem(@RequestBody MenuItemDTO request) {
        return ResponseEntity.ok(ApiResponse.success("Menu item created", menuItemService.createMenuItem(request)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<MenuItemDTO>> updateMenuItem(@PathVariable Integer id,
                                                                   @RequestBody MenuItemDTO request) {
        return ResponseEntity.ok(ApiResponse.success("Menu item updated", menuItemService.updateMenuItem(id, request)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteMenuItem(@PathVariable Integer id) {
        menuItemService.deleteMenuItem(id);
        return ResponseEntity.ok(ApiResponse.success("Menu item deleted", null));
    }


    @GetMapping("/permissions/role/{roleId}")
    public ResponseEntity<ApiResponse<List<Integer>>> getRolePermissions(@PathVariable Integer roleId) {
        return ResponseEntity.ok(ApiResponse.success(menuItemService.getMenuItemIdsForRole(roleId)));
    }

    // Endpoint pour sauvegarder (utilisé par le bouton Enregistrer)
    @PostMapping("/permissions/save")
    public ResponseEntity<ApiResponse<Void>> savePermissions(@RequestBody RoleMenuRequest request) {
        menuItemService.saveRoleMenuMappings(request.getRoleId(), request.getMenuItemIds());
        return ResponseEntity.ok(ApiResponse.success("Permissions mises à jour avec succès", null));
    }
}