package com.gulfnet.shared_library.model.request;

import com.gulfnet.shared_library.enums.EntityStatus;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
public class CategoryRequest {
    private UUID menuStructureId;
    private UUID parentCategoryId;
    private EntityStatus status;
    private List<CategoryTranslationRequest> translations;
    private Integer displayOrder;
    private Boolean isCombo;

    public Boolean getIsCombo() {
        return isCombo;
    }

    public void setIsCombo(Boolean isCombo) {
        this.isCombo = isCombo;
    }
}