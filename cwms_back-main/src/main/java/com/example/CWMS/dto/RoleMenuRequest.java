package com.example.CWMS.dto;

import lombok.*;
import jakarta.validation.constraints.*;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RoleMenuRequest {
    @NotNull
    private Integer roleId;

    @NotNull
    private List<Integer> menuItemIds;
}