package com.gulfnet.shared_library.model.response.dto;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@ToString
public class RestaurantChainTranslationDto {
    @JsonIgnore
    private String languageId;

    private String languageCode;
    @NotBlank(message = "Name must not be blank")
    @Size(max = 255, message = "Name must not exceed 255 characters")
    private String name;


} 