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
public class SimpleEncryptedAuthRequest {

    @JsonProperty("encryptedAppKey")
    @NotBlank(message = "Encrypted app key is required")
    private String encryptedAppKey; // RSA-encrypted AES key (Base64)
}
