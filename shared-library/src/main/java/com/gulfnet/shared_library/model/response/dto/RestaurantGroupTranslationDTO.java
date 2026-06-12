package com.gulfnet.shared_library.model.response.dto;


import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;


@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@ToString

public class RestaurantGroupTranslationDTO {

    private String languageCode;
    @Size(max = 255, message = "Name must not exceed 255 characters")
    private String name;

   
    public String getLanguageCode() { return languageCode; }
    public void setLanguageCode(String languageCode) { this.languageCode = languageCode; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
}