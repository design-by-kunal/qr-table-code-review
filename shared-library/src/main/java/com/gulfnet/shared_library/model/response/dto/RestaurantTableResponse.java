package com.gulfnet.shared_library.model.response.dto;

import com.gulfnet.shared_library.enums.TableShape;
import com.gulfnet.shared_library.enums.TableStatus;
import lombok.*;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RestaurantTableResponse {

    private UUID id;

    private Integer tableOrder;

    private TableShape shape;

    private Integer capacity;

    private TableStatus tableStatus;

    private String tableCode;
}
