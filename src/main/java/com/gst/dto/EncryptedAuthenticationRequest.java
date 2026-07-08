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
public class EncryptedAuthenticationRequest {

    @NotBlank(message = "Client ID is required")
    private String clientId; // Header: X-Client-ID

    // clientSecret sent in encrypted payload (as Base64)
    @NotBlank(message = "Encrypted payload is required")
    private String encryptedPayload; // Base64 RSA-encrypted JSON containing clientSecret + rsaPublicKey

    // Structure of decrypted payload:
    // {
    //   "clientSecret": "secret-key-123",
    //   "rsaPublicKey": "-----BEGIN PUBLIC KEY-----\nMIIBIjANBg...\n-----END PUBLIC KEY-----"
    // }
}
