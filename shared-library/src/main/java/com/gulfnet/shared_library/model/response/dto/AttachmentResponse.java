package com.gulfnet.shared_library.model.response.dto;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

@AllArgsConstructor
@Getter
@Setter
@ToString
@Builder
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AttachmentResponse {

    private String fileName;
    private String fileType;
    private String fileLocation;
    private String preSignedUrl;
    private String attachmentType;
    private String errorMessage;

}
