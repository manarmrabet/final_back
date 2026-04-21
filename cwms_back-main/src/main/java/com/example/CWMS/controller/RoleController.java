package com.example.CWMS.controller;

import com.example.CWMS.dto.*;
import com.example.CWMS.service.RoleServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/admin/roles")
@RequiredArgsConstructor
public class RoleController {

    private final RoleServiceImpl roleService;
    @GetMapping

    public ResponseEntity<ApiResponse<List<RoleDTO>>> getAllRoles() {
        return ResponseEntity.ok(ApiResponse.success(roleService.getAllRoles()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<RoleDTO>> getRoleById(@PathVariable Integer id) {
        return ResponseEntity.ok(ApiResponse.success(roleService.getRoleById(id)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<RoleDTO>> createRole(@RequestBody RoleDTO request) {
        return ResponseEntity.ok(ApiResponse.success("Role created", roleService.createRole(request)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<RoleDTO>> updateRole(@PathVariable Integer id,
                                                           @RequestBody RoleDTO request) {
        return ResponseEntity.ok(ApiResponse.success("Role updated", roleService.updateRole(id, request)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteRole(@PathVariable Integer id) {
        roleService.deleteRole(id);
        return ResponseEntity.ok(ApiResponse.success("Role deleted", null));
    }

    @PostMapping("/assign-menus")
    public ResponseEntity<ApiResponse<Void>> assignMenus(@RequestBody RoleMenuRequest request) {
        roleService.assignMenusToRole(request);
        return ResponseEntity.ok(ApiResponse.success("Menus assigned to role", null));
    }

    @GetMapping("/{id}/menus")
    public ResponseEntity<ApiResponse<List<Integer>>> getRoleMenus(@PathVariable Integer id) {
        return ResponseEntity.ok(ApiResponse.success(roleService.getMenuIdsByRole(id)));
    }
}