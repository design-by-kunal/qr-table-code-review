package com.gulfnet.shared_library.model.request;

import java.util.Map;

public class RestaurantGroupCreateRequest {
    private String restaurantGroupCode;
    private String status;
    private Boolean isDeleted;
    private Map<String, String> translations; // languageCode -> name

    
    public String getRestaurantGroupCode() { return restaurantGroupCode; }
    public void setRestaurantGroupCode(String restaurantGroupCode) { this.restaurantGroupCode = restaurantGroupCode; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Boolean getIsDeleted() { return isDeleted; }
    public void setIsDeleted(Boolean isDeleted) { this.isDeleted = isDeleted; }
    public Map<String, String> getTranslations() { return translations; }
    public void setTranslations(Map<String, String> translations) { this.translations = translations; }
} 