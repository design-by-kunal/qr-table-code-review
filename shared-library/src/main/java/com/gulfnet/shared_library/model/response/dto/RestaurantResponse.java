package com.gulfnet.shared_library.model.response.dto;
import com.gulfnet.shared_library.enums.EntityStatus;
import com.gulfnet.shared_library.enums.QrCodeType;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;
import java.util.List;
import lombok.*;

@AllArgsConstructor
@Getter
@Setter
@ToString
@Builder
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class RestaurantResponse {
    private String uuid;
    private String restaurantCode;
    private String city;
    private String area;
    private String state;
    private String address1;
    private String address2;
    private String latitude;
    private String longitude;
    private String locationPin;
    private String countryName;
    
    private String paymentQrUrl;
    private EntityStatus status;
    private QrCodeType tableQrCodeType;

    private String createdBy;
    private String updatedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    private String restaurantGroupId;

    private String logoUrl;
    private Boolean isDeleted;
    private List<RestaurantTranslationDto> translations;

    // Added fields for group name and employee count
    private String restaurantGroupName;
    private Integer employeeCount;

    // Added field for operating hours
    private List<OperatingHourDto> operatingHours;

    private List<RestaurantGroupTranslationDTO> restaurantGroupNames;
    
    // Added fields for active discount and promotion counts
    private Long activeDiscountCount;
    private Long activePromotionCount;

    // Added field for total seating capacity
    private Integer seatingCapacity;

    // Indicates whether the restaurant has any menu assigned/published
    private Boolean menuPublished;

    // Added field for GST Number
    private String gstNumber;

    /** Contact phone number */
    private String phoneNumber;

    // Alert Configuration Fields
    private java.math.BigDecimal salesAlertThreshold;
    private java.math.BigDecimal refundAlertPercentage;
    private java.math.BigDecimal cancellationAlertPercentage;
    private Boolean alertsEnabled;

    /**
     * Gets the restaurant name from translations, falling back to restaurantCode if no translations exist.
     * This method is used for sorting and display purposes.
     * 
     * @return the restaurant name from translations, or restaurantCode as fallback
     */
    @JsonProperty("name")
    public String getName() {
        if (translations != null && !translations.isEmpty()) {
            // Return the name from the first translation
            RestaurantTranslationDto firstTranslation = translations.get(0);
            if (firstTranslation != null && firstTranslation.getName() != null && !firstTranslation.getName().trim().isEmpty()) {
                return firstTranslation.getName();
            }
        }
        // Fallback to restaurantCode if no translations or empty name
        return restaurantCode != null ? restaurantCode : "";
    }
}



