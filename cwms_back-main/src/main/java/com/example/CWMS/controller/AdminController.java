package com.example.CWMS.controller;

import com.example.CWMS.dto.ApiResponse; // Import de ton DTO ApiResponse
import com.example.CWMS.dto.UserDTO;
import com.example.CWMS.iservice.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    @Autowired
    private UserService userService;

    // 1. Dashboard : Simple message de bienvenue
    @GetMapping("/dashboard")
    public ResponseEntity<ApiResponse<String>> getAdminStats() {
        return ResponseEntity.ok(ApiResponse.success("Bienvenue sur le panneau d'administration."));
    }

    // 2. Liste de tous les utilisateurs
    @GetMapping("/users")
    public ResponseEntity<ApiResponse<List<UserDTO>>> listUsers() {
        List<UserDTO> users = userService.getAllUsers();
        return ResponseEntity.ok(ApiResponse.success(users));
    }

    // 3. Récupérer un utilisateur spécifique
    @GetMapping("/users/{id}")
    public ResponseEntity<ApiResponse<UserDTO>> getUserById(@PathVariable Integer id) {
        UserDTO user = userService.getUserById(id);
        return ResponseEntity.ok(ApiResponse.success(user));
    }

    // 4. Créer un nouvel utilisateur
    @PostMapping("/users")
    public ResponseEntity<ApiResponse<UserDTO>> createUser(@RequestBody UserDTO userDTO) {
        UserDTO createdUser = userService.createUser(userDTO);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Utilisateur créé avec succès", createdUser));
    }

    // 5. Modifier un utilisateur
    @PutMapping("/users/{id}")
    public ResponseEntity<ApiResponse<UserDTO>> updateUser(@PathVariable Integer id, @RequestBody UserDTO userDTO) {
        UserDTO updatedUser = userService.updateUser(id, userDTO);
        return ResponseEntity.ok(ApiResponse.success("Utilisateur mis à jour", updatedUser));
    }@DeleteMapping("/users/{id}")
    public ResponseEntity<?> deleteUser(@PathVariable Integer id) {
        try {
            userService.deleteUser(id);
            return ResponseEntity.ok(ApiResponse.success("Utilisateur supprimé (traçabilité conservée)", null));
        } catch (RuntimeException e) {
            return ResponseEntity.status(404).body(ApiResponse.error(e.getMessage()));
        }
    }

    @DeleteMapping("/users/{id}/force")
    public ResponseEntity<?> forceDeleteUser(@PathVariable Integer id) {
        try {
            userService.forceDeleteUser(id);
            return ResponseEntity.ok(ApiResponse.success("Utilisateur et traçabilité supprimés définitivement", null));
        } catch (RuntimeException e) {
            return ResponseEntity.status(404).body(ApiResponse.error(e.getMessage()));
        }
    }
}