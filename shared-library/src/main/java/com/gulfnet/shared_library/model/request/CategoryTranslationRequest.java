package com.gulfnet.shared_library.model.request;

import lombok.Data;

@Data
public class CategoryTranslationRequest {
    private String languageCode;
    private String name;
}