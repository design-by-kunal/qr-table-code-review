package com.gulfnet.shared_library.model.response.dto;
import lombok.*;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@ToString
@JsonIgnoreProperties(ignoreUnknown = true)
public class PaginationMetaData {
    private int page;
    private int size;
    private int totalPages;
    private long totalRecords;
}
