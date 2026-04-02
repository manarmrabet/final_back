package com.example.CWMS.iservice;

import com.example.CWMS.dto.MenuItemDTO;
import com.example.CWMS.model.cwms.MenuItem;

import java.util.List;

public interface MenuItemService {
    List<MenuItemDTO> getMenuItemsForCurrentUser();
    List<MenuItemDTO> getAllMenuItems();
    MenuItemDTO createMenuItem(MenuItemDTO request);
    MenuItemDTO updateMenuItem(Integer id, MenuItemDTO request);
    void deleteMenuItem(Integer id);
    MenuItemDTO toDTO(MenuItem item);
    void saveRoleMenuMappings(Integer roleId, List<Integer> menuItemIds);
    List<Integer> getMenuItemIdsForRole(Integer roleId);
}
