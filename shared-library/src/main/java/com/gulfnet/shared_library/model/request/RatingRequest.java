package com.gulfnet.shared_library.model.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RatingRequest {

    @NotNull(message = "{rating.experience.required}")
    @Min(value = 1, message = "{rating.experience.min}")
    @Max(value = 5, message = "{rating.experience.max}")
    private Integer experience;

    @NotNull(message = "{rating.food.required}")
    @Min(value = 1, message = "{rating.food.min}")
    @Max(value = 5, message = "{rating.food.max}")
    private Integer food;

    @NotNull(message = "{rating.service.required}")
    @Min(value = 1, message = "{rating.service.min}")
    @Max(value = 5, message = "{rating.service.max}")
    private Integer service;

    private String feedback;
}

