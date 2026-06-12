package com.gulfnet.shared_library.entity;

import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class MenuPromotionId implements Serializable {
    private UUID menuId;
    private UUID promotionId;

    public MenuPromotionId() {}

    public MenuPromotionId(UUID menuId, UUID promotionId) {
        this.menuId = menuId;
        this.promotionId = promotionId;
    }

    public UUID getMenuId() {
        return menuId;
    }

    public void setMenuId(UUID menuId) {
        this.menuId = menuId;
    }

    public UUID getPromotionId() {
        return promotionId;
    }

    public void setPromotionId(UUID promotionId) {
        this.promotionId = promotionId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MenuPromotionId)) return false;
        MenuPromotionId that = (MenuPromotionId) o;
        return Objects.equals(menuId, that.menuId) &&
                Objects.equals(promotionId, that.promotionId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(menuId, promotionId);
    }
}
