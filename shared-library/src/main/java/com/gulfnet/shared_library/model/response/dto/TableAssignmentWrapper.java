package com.gulfnet.shared_library.model.response.dto;

import lombok.*;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Builder
@ToString
public class TableAssignmentWrapper<T> {
    private T tableAssignment;

}
