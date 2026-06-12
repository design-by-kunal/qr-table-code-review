package com.gulfnet.shared_library.model.request;

import com.gulfnet.shared_library.enums.EntityStatus;
import com.gulfnet.shared_library.enums.QrCodeType;
import com.gulfnet.shared_library.model.response.dto.RestaurantTranslationDto;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.util.List;
import java.util.UUID;

@AllArgsConstructor
@Getter
@Setter
@ToString
@Builder
@NoArgsConstructor
public class RestaurantRequest {

    private UUID restaurantGroupId;

    private String restaurantCode;

    @NotBlank(message = "{restaurant.city.blank}")
    private String city;

    @NotBlank(message = "{restaurant.area.blank}")
    private String area;

    @NotBlank(message = "{restaurant.state.blank}")
    private String state;

    @NotBlank(message = "{restaurant.address1.blank}")
    private String address1;

    private String address2; // Optional

    private String latitude;

    private String longitude;

    @NotBlank(message = "{restaurant.locationPin.blank}")
    private String locationPin;
    @Builder.Default
    private Boolean isDeleted = false;

    @NotBlank(message = "{restaurant.logoUrl.blank}")
    private String logoUrl;

    @NotBlank(message = "{restaurant.paymentQrUrl.blank}")
    private String paymentQrUrl;

    @NotNull(message = "{restaurant.tableQrCodeType.required}")
    private QrCodeType tableQrCodeType;

    @NotNull(message = "{restaurant.status.required}")
    private EntityStatus status;


    @NotNull(message = "{restaurant.translations.required}")
    @Size(min = 1, message = "{restaurant.translations.min.one}")
    private List<RestaurantTranslationDto> translations;

    // Added field for group name
    private String restaurantGroupName;

    // Added field for operating hours
    private List<RestaurantOperatingHoursRequest> operatingHours;

    // Added field for GST Number
    private String gstNumber;

    /**
     * Optional contact phone; trimmed when persisted, blank stored as null.
     * When set: 7–15 digits, at most 32 characters, only +, digits, spaces, parentheses, hyphens, dots.
     */
    @Pattern(
            regexp = "^$|^(?=(?:[^\\d]*\\d){7,15}[^\\d]*$)[+\\d\\s().-]{7,32}$",
            message = "{restaurant.phoneNumber.invalid}")
    private String phoneNumber;

    // Alert Configuration Fields
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

    public List<RestaurantTranslationDto> getTranslations() {
        return translations;
    }
}
