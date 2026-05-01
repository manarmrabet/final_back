package com.example.CWMS.dto;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import lombok.*;
import jakarta.validation.constraints.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor


public class RoleMenuRequest {
    @NotNull
    private Integer roleId;

    @NotNull
    private List<Integer> menuItemIds;

    // ✅ FIX SpotBugs — copie défensive setter
    public void setMenuItemIds(List<Integer> menuItemIds) {
        this.menuItemIds = menuItemIds == null ? null : new ArrayList<>(menuItemIds);
    }

    // ✅ FIX SpotBugs — protection getter
    public List<Integer> getMenuItemIds() {
        return menuItemIds == null ? null : Collections.unmodifiableList(menuItemIds);
    }
}