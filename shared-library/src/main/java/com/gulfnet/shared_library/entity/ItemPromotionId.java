package com.gulfnet.shared_library.entity;

import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class ItemPromotionId implements Serializable {
    private UUID itemId;
    private UUID promotionId;

    public ItemPromotionId() {}

    public ItemPromotionId(UUID itemId, UUID promotionId) {
        this.itemId = itemId;
        this.promotionId = promotionId;
    }

    public UUID getItemId() {
        return itemId;
    }

    public void setItemId(UUID itemId) {
        this.itemId = itemId;
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
        if (!(o instanceof ItemPromotionId)) return false;
        ItemPromotionId that = (ItemPromotionId) o;
        return Objects.equals(itemId, that.itemId) &&
                Objects.equals(promotionId, that.promotionId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(itemId, promotionId);
    }
}

