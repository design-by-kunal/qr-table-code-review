package com.gulfnet.shared_library.entity;

import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.Data;
import java.io.Serializable;
import java.util.UUID;

@Data
@Embeddable

public class CategoryItemId implements Serializable {
    private UUID categoryId;
    private UUID itemId;

    public CategoryItemId() {}

    public CategoryItemId(UUID categoryId, UUID itemId) {
        this.categoryId = categoryId;
        this.itemId = itemId;
    }

    public UUID getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(UUID categoryId) {
        this.categoryId = categoryId;
    }

    public UUID getItemId() {
        return itemId;
    }

    public void setItemId(UUID itemId) {
        this.itemId = itemId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CategoryItemId)) return false;
        CategoryItemId that = (CategoryItemId) o;
        return Objects.equals(categoryId, that.categoryId) && 
               Objects.equals(itemId, that.itemId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(categoryId, itemId);
    }

}