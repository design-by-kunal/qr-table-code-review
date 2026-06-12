package com.gulfnet.shared_library.model.response.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SectionResponseV2 {
    private String sectionId;
    private Integer sectionNumber;
    private String sectionName;
    private List<RowResponseV2> rows;
}

