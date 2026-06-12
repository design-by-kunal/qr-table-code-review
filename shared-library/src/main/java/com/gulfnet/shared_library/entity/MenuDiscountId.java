package com.gulfnet.shared_library.entity;

import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class MenuDiscountId implements Serializable {

    private UUID menuId;
    private UUID discountId;

    // Default constructor
    public MenuDiscountId() {}

    public MenuDiscountId(UUID menuId, UUID discountId) {
        this.menuId = menuId;
        this.discountId = discountId;
    }

    // Getters and Setters
    public UUID getMenuId() {
        return menuId;
    }

    public void setMenuId(UUID menuId) {
        this.menuId = menuId;
    }

    public UUID getDiscountId() {
        return discountId;
    }

    public void setDiscountId(UUID discountId) {
        this.discountId = discountId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MenuDiscountId)) return false;
        MenuDiscountId that = (MenuDiscountId) o;
        return Objects.equals(menuId, that.menuId) && Objects.equals(discountId, that.discountId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(menuId, discountId);
    }
} 