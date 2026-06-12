package com.gulfnet.shared_library.model.response.dto;

import lombok.*;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.gulfnet.shared_library.model.response.dto.ErrorDto;
import com.gulfnet.shared_library.model.response.dto.PaginationMetaData;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @ToString
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public class ResponseDto<T> {
        private String message;
        private T data;
        private Long count;
        private Long total;
        private List<ErrorDto> errors;
        private PaginationMetaData metaData;
    }
    
      

