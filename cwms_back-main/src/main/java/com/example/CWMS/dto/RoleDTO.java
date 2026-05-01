package com.example.CWMS.dto;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import lombok.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
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

    // ✅ FIX SpotBugs — copie défensive setter
    public void setMenuItemIds(List<Integer> menuItemIds) {
        this.menuItemIds = menuItemIds == null ? null : new ArrayList<>(menuItemIds);
    }

    // ✅ FIX SpotBugs — protection getter
    public List<Integer> getMenuItemIds() {
        return menuItemIds == null ? null : Collections.unmodifiableList(menuItemIds);
    }
}