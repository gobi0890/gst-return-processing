package com.gst.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotBlank;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EncryptedRequest {

    @JsonProperty("encrypted_payload")
    @NotBlank(message = "Encrypted payload is required")
    private String encryptedPayload; // Base64-encoded encrypted request body

    @JsonProperty("timestamp")
    private Long timestamp; // Request timestamp for replay attack prevention
}
