package com.gst.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotBlank;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuthenticationPayload {

    @NotBlank(message = "Client Secret is required")
    private String clientSecret;

    @NotBlank(message = "RSA Public Key is required")
    private String rsaPublicKey; // Client's public key for response encryption
}
