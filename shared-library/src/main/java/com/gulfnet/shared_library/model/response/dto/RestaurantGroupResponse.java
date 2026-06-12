package com.gulfnet.shared_library.model.response.dto;
import java.time.LocalDateTime;
import java.util.List;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RestaurantGroupResponse {
    private String uuid;
    private String restaurantGroupCode;
    private String status;
    private LocalDateTime createdAt;
    private String createdBy;
    private LocalDateTime updatedAt;
    private String updatedBy;
    private Boolean isPublished;
    private Long restaurantCount;
    private List<RestaurantGroupTranslationDTO> translations;
    private List<RestaurantResponse> restaurants;
    
    // Added fields for total active discount and promotion counts across all restaurants
    private Long activeDiscountCount;
    private Long activePromotionCount;

    // Alert Configuration Fields (group-level)
    private java.math.BigDecimal salesAlertThreshold;

    @DecimalMin(value = "0.00", inclusive = true,
            message = "Refund alert percentage must be between 0 and 100.")
    @DecimalMax(value = "100.00", inclusive = true,
            message = "Refund alert percentage must be between 0 and 100.")
    private java.math.BigDecimal refundAlertPercentage;

    @DecimalMin(value = "0.00", inclusive = true,
            message = "Cancellation alert percentage must be between 0 and 100.")
    @DecimalMax(value = "100.00", inclusive = true,
            message = "Cancellation alert percentage must be between 0 and 100.")
    private java.math.BigDecimal cancellationAlertPercentage;
    private Boolean alertsEnabled;

    public String getUuid() { return uuid; }
    public void setUuid(String uuid) { this.uuid = uuid; }
    public String getRestaurantGroupCode() { return restaurantGroupCode; }
    public void setRestaurantGroupCode(String restaurantGroupCode) { this.restaurantGroupCode = restaurantGroupCode; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public String getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }
    public Long getRestaurantCount() { return restaurantCount; }
    public void setRestaurantCount(Long restaurantCount) { this.restaurantCount = restaurantCount; }
    public List<RestaurantGroupTranslationDTO> getTranslations() { return translations; }
    public void setTranslations(List<RestaurantGroupTranslationDTO> translations) { this.translations = translations; }
    public List<RestaurantResponse> getRestaurants() { return restaurants; }
    public void setRestaurants(List<RestaurantResponse> restaurants) { this.restaurants = restaurants; }
    
    public Long getActiveDiscountCount() { return activeDiscountCount; }
    public void setActiveDiscountCount(Long activeDiscountCount) { this.activeDiscountCount = activeDiscountCount; }
    public Long getActivePromotionCount() { return activePromotionCount; }
    public void setActivePromotionCount(Long activePromotionCount) { this.activePromotionCount = activePromotionCount; }
}
