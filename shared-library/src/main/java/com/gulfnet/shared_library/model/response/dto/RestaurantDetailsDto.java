package com.gulfnet.shared_library.model.response.dto;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.gulfnet.shared_library.enums.QrCodeType;

import lombok.*;


@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RestaurantDetailsDto {
    private UUID sessionId;
    private UUID restaurantId;
    private UUID tableId;
    private String countryName;
    private Integer tableOrder;
    private QrCodeType qrCodeType;
    private Integer sequenceNo;
    private String tableCode;
    private Boolean isVirtual; // Flag indicating if the table is virtual
}
