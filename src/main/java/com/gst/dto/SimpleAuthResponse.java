package com.gst.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SimpleAuthResponse {

    @JsonProperty("auth_token")
    private String authToken; // JWT Token (30 min expiration)

    @JsonProperty("sek")
    private String sek; // Session Encryption Key (Base64 AES-256 key)

    @JsonProperty("token_type")
    private String tokenType; // "Bearer"

    @JsonProperty("expires_in")
    private Long expiresIn; // Expiration time in milliseconds
}
