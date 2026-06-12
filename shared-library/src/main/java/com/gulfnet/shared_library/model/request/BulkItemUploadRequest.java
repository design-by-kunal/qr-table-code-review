package com.gulfnet.shared_library.model.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BulkItemUploadRequest {
    private String itemCode;
    private String basePrice;
    private String imageUrl;
    private String outOfStock;
    private String status;
    private String dietaryPreference;
    private String alcoholType;
    private String itemOrderType;
    private String nameEn;
    private String descriptionEn;
    private String nameJa;
    private String descriptionJa;
    private String nameTh;
    private String descriptionTh;
    private String imageName;
    // Generic language support
    private Map<String, String> nameByLanguage;        // key: languageCode, value: name
    private Map<String, String> descriptionByLanguage; // key: languageCode, value: description

    // Explicit getters/setters to ensure availability across modules
    public String getAlcoholType() {
        return alcoholType;
    }
    public void setAlcoholType(String alcoholType) {
        this.alcoholType = alcoholType;
    }

    public String getItemOrderType() {
        return itemOrderType;
    }
    public void setItemOrderType(String itemOrderType) {
        this.itemOrderType = itemOrderType;
    }

    public Map<String, String> getNameByLanguage() {
        return nameByLanguage;
    }
    public void setNameByLanguage(Map<String, String> nameByLanguage) {
        this.nameByLanguage = nameByLanguage;
    }
    public Map<String, String> getDescriptionByLanguage() {
        return descriptionByLanguage;
    }
    public void setDescriptionByLanguage(Map<String, String> descriptionByLanguage) {
        this.descriptionByLanguage = descriptionByLanguage;
    }
}