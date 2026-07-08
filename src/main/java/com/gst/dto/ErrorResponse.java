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
public class ErrorResponse {

    private String errorCode; // Custom error code
    private String message;
    private String details; // Additional details about the error
    private String traceId;
    private LocalDateTime timestamp;

    public static ErrorResponseBuilder builder() {
        return new ErrorResponseBuilder().timestamp(LocalDateTime.now());
    }
}
