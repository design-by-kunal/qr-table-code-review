package com.gulfnet.shared_library.entity;

import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class MenuCategoryId implements Serializable {

    private UUID menuId;
    private UUID categoryId;

    // Default constructor
    public MenuCategoryId() {}

    public MenuCategoryId(UUID menuId, UUID categoryId) {
        this.menuId = menuId;
        this.categoryId = categoryId;
    }

    // Getters and Setters
    public UUID getMenuId() {
        return menuId;
    }

    public void setMenuId(UUID menuId) {
        this.menuId = menuId;
    }

    public UUID getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(UUID categoryId) {
        this.categoryId = categoryId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MenuCategoryId)) return false;
        MenuCategoryId that = (MenuCategoryId) o;
        return Objects.equals(menuId, that.menuId) &&
        Objects.equals(categoryId, that.categoryId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(menuId, categoryId);
    }
}