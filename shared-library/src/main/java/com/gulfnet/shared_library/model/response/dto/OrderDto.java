package com.gulfnet.shared_library.model.response.dto;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderDto<T> {
    private T order;
}
