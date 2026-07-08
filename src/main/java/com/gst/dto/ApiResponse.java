package com.gst.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    private Boolean success;
    private String statusCode; // Custom status code (e.g., "SUCCESS", "INVALID_CLIENT", "UNAUTHORIZED")
    private String message;
    private T data;
    private String traceId;
    private LocalDateTime timestamp;

    @Builder.Default
    private Integer httpStatusCode = 200;

    public static <T> ApiResponseBuilder<T> builder() {
        return new ApiResponseBuilder<T>().timestamp(LocalDateTime.now());
    }
}
