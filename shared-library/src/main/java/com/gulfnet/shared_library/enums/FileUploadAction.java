package com.gulfnet.shared_library.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;

import java.util.Arrays;
import java.util.Optional;

@Getter
@ToString
@RequiredArgsConstructor
public enum FileUploadAction {

    PORTFOLIO("portfolio"),
    REPORTS("reports"),
    PROFILE_PHOTO("profile photo"),
    SERVICE_ICON("service icon"),
    INVOICE("invoice"),
    BULK_UPLOAD_EMPLOYEE("bulk upload employee"),
    BULK_UPLOAD_ITEMS("bulk upload items"),
    BULK_UPLOAD_RESTAURANTS("bulk upload restaurants"),
    PROFILE_IMAGE_RESTAURANT("profile image restaurant"),
    PROFILE_IMAGE_RESTAURANT_GROUP("profile image restaurant group"),
    PROFILE_IMAGE_EMPLOYEE("profile image employee"),
    ITEM_IMAGE("item image"),
    MODIFIER_ITEM_IMAGE("modifier item image"),
    PROMOTION_IMAGE("promotion image"),
    PAYMENT_METHODS("payment methods"),
    PAYMENT_APPS("payment apps");

    private final String fileAction;

    @JsonCreator
    public static FileUploadAction fromValue(String fileAction) {
        return Arrays.stream(FileUploadAction.values())
                .filter(env -> env.getFileAction().equalsIgnoreCase(fileAction))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Invalid action for file upload: " + fileAction));
    }

    public static Optional<FileUploadAction> get(String fileAction){
        return Arrays.stream(FileUploadAction.values())
                .filter(env -> env.getFileAction().equalsIgnoreCase(fileAction))
                .findFirst();
    }
}
