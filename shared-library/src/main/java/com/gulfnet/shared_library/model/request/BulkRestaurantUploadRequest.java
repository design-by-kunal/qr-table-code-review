package com.gulfnet.shared_library.model.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BulkRestaurantUploadRequest {
    private String nameEn;
    private String nameJa;
    private String nameTh;
    private String restaurantCode;
    private String restaurantGroupCode;
    private String city;
    private String area;
    private String state;
    private String addressLine1;
    private String addressLine2;
    private String latitude;
    private String longitude;
    private String locationPin;
    private String qrCodeType;
    private String status;
    private String logoName;
    private String gstNumber;
    /** Optional; trimmed when saved to restaurant. */
    private String phoneNumber;
    
    // Dynamic language support
    private Map<String, String> languageNames = new HashMap<>();
    
    /**
     * Constructor for backward compatibility with existing bulk upload formats.
     * Initializes the restaurant data with English, Japanese, and Thai names,
     * and automatically populates the language names map.
     *
     * @param nameEn              English name of the restaurant
     * @param nameJa              Japanese name of the restaurant
     * @param nameTh              Thai name of the restaurant
     * @param restaurantCode      unique code for the restaurant
     * @param restaurantGroupCode code of the restaurant group this restaurant belongs to
     * @param city                city where the restaurant is located
     * @param area                area/neighborhood where the restaurant is located
     * @param state               state/province where the restaurant is located
     * @param addressLine1        primary address line
     * @param addressLine2        secondary address line (optional)
     * @param latitude            geographic latitude coordinate
     * @param longitude           geographic longitude coordinate
     * @param locationPin         location pin identifier
     * @param qrCodeType         type of QR code used
     * @param status              status of the restaurant (ACTIVE, INACTIVE, etc.)
     * @param logoName            name/path of the restaurant logo file
     * @param gstNumber          GST (tax) number for the restaurant
     */
    public BulkRestaurantUploadRequest(String nameEn, String nameJa, String nameTh, String restaurantCode, 
                                     String restaurantGroupCode, String city, String area, String state, 
                                     String addressLine1, String addressLine2, String latitude, String longitude, 
                                     String locationPin, String qrCodeType, String status, String logoName, String gstNumber) {
        this.nameEn = nameEn;
        this.nameJa = nameJa;
        this.nameTh = nameTh;
        this.restaurantCode = restaurantCode;
        this.restaurantGroupCode = restaurantGroupCode;
        this.city = city;
        this.area = area;
        this.state = state;
        this.addressLine1 = addressLine1;
        this.addressLine2 = addressLine2;
        this.latitude = latitude;
        this.longitude = longitude;
        this.locationPin = locationPin;
        this.qrCodeType = qrCodeType;
        this.status = status;
        this.logoName = logoName;
        this.gstNumber = gstNumber;
        
        // Initialize language names map
        if (nameEn != null) languageNames.put("en", nameEn);
        if (nameJa != null) languageNames.put("ja", nameJa);
        if (nameTh != null) languageNames.put("th", nameTh);
    }
    
    /**
     * Get name for a specific language
     */
    public String getNameForLanguage(String languageCode) {
        return languageNames.get(languageCode);
    }
    
    /**
     * Set name for a specific language
     */
    public void setNameForLanguage(String languageCode, String name) {
        languageNames.put(languageCode, name);
    }
    
    /**
     * Get all language names
     */
    public Map<String, String> getAllLanguageNames() {
        return new HashMap<>(languageNames);
    }
} 