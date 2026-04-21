package com.example.CWMS.dto;
import lombok.*;

import java.time.LocalDateTime;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder

public class RoleDTO {
    private Integer roleId;
    private String roleName;
    private String description;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<Integer> menuItemIds;
    private Integer userCount;
}