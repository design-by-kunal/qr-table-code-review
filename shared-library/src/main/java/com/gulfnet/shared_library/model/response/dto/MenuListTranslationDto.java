package com.gulfnet.shared_library.model.response.dto;
import lombok.*;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MenuListTranslationDto {
    private String languageCode;
    private String name;
}
