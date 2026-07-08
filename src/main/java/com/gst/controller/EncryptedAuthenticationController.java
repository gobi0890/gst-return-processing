package com.gst.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gst.dto.*;
import com.gst.entity.Client;
import com.gst.entity.ClientSession;
import com.gst.repository.ClientRepository;
import com.gst.security.ClientAuthenticationService;
import com.gst.security.EncryptionService;
import com.gst.security.JwtTokenProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
public class EncryptedAuthenticationController {

    @Autowired
    private ClientAuthenticationService clientAuthService;

    @Autowired
    private EncryptionService encryptionService;

    @Autowired
    private JwtTokenProvider tokenProvider;

    @Autowired
    private ClientRepository clientRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * NEW FLOW: Encrypted Login
     * 
     * Request Header: X-Client-ID: client-001
     * 
     * Request Body (RSA-encrypted with client's own private key):
     * {
     *   "encryptedPayload": "<BASE64_ENCRYPTED_DATA>"
     * }
     * 
     * Encrypted data contains:
     * {
     *   "clientSecret": "secret-key-123",
     *   "rsaPublicKey": "-----BEGIN PUBLIC KEY-----...-----END PUBLIC KEY-----"
     * }
     */
    @PostMapping("/login/encrypted")
    public ResponseEntity<ApiResponse<?>> encryptedLogin(
            @RequestHeader("X-Client-ID") String clientId,
            @RequestBody EncryptedAuthenticationRequest request) {
        
        String traceId = UUID.randomUUID().toString();
        long startTime = System.currentTimeMillis();

        try {
            log.info("[{}] Encrypted authentication attempt for client: {}", traceId, clientId);

            // Step 1: Verify client exists and is active
            Optional<Client> clientOpt = clientRepository.findByClientIdAndIsActive(clientId, true);
            if (clientOpt.isEmpty()) {
                log.warn("[{}] Client not found or inactive: {}", traceId, clientId);
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(ApiResponse.builder()
                                .success(false)
                                .statusCode("INVALID_CLIENT")
                                .message("Invalid client ID")
                                .traceId(traceId)
                                .httpStatusCode(401)
                                .build());
            }

            Client client = clientOpt.get();
            log.debug("[{}] Client found: {}", traceId, clientId);

            // Step 2: Decrypt the payload using client's private key
            String decryptedPayload;
            try {
                PrivateKey clientPrivateKey = parsePrivateKey(client.getRsaPrivateKeyPem());
                decryptedPayload = encryptionService.decryptWithPrivateKey(request.getEncryptedPayload(), clientPrivateKey);
                log.debug("[{}] Payload decrypted successfully for client: {}", traceId, clientId);
            } catch (Exception e) {
                log.error("[{}] Failed to decrypt payload for client: {} - {}", traceId, clientId, e.getMessage());
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(ApiResponse.builder()
                                .success(false)
                                .statusCode("DECRYPTION_FAILED")
                                .message("Failed to decrypt payload")
                                .traceId(traceId)
                                .httpStatusCode(400)
                                .build());
            }

            // Step 3: Parse the decrypted payload
            AuthenticationPayload authPayload;
            try {
                authPayload = objectMapper.readValue(decryptedPayload, AuthenticationPayload.class);
                log.debug("[{}] Authentication payload parsed for client: {}", traceId, clientId);
            } catch (Exception e) {
                log.error("[{}] Failed to parse authentication payload: {}", traceId, e.getMessage());
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(ApiResponse.builder()
                                .success(false)
                                .statusCode("INVALID_PAYLOAD")
                                .message("Invalid authentication payload format")
                                .traceId(traceId)
                                .httpStatusCode(400)
                                .build());
            }

            // Step 4: Verify client credentials (SHA-512 hash)
            if (!clientAuthService.verifyClientCredentials(clientId, authPayload.getClientSecret())) {
                log.warn("[{}] Invalid credentials for client: {}", traceId, clientId);
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(ApiResponse.builder()
                                .success(false)
                                .statusCode("INVALID_CREDENTIALS")
                                .message("Invalid client secret")
                                .traceId(traceId)
                                .httpStatusCode(401)
                                .build());
            }

            // Step 5: Generate SEK (Session Encryption Key)
            String sek = UUID.randomUUID().toString();
            log.debug("[{}] Generated SEK for client: {}", traceId, clientId);

            // Step 6: Generate JWT Auth Token (30 minutes)
            String authToken = tokenProvider.generateToken(clientId, authPayload.getClientSecret());
            log.debug("[{}] Generated JWT token for client: {}", traceId, clientId);

            // Step 7: Create client session
            ClientSession session = clientAuthService.createClientSession(clientId, sek);
            log.info("[{}] Client session created for: {}", traceId, clientId);

            // Step 8: Prepare authentication response
            AuthenticationResponse authResponse = AuthenticationResponse.builder()
                    .authToken(authToken)
                    .sek(sek)
                    .tokenType("Bearer")
                    .expiresIn(tokenProvider.getExpirationTime())
                    .build();

            // Step 9: Encrypt response with client's RSA public key
            String responseJson = objectMapper.writeValueAsString(authResponse);
            PublicKey clientPublicKey = parsePublicKey(authPayload.getRsaPublicKey());
            String encryptedResponse = encryptionService.encryptWithPublicKey(responseJson, clientPublicKey);
            log.debug("[{}] Response encrypted for client: {}", traceId, clientId);

            long responseTime = System.currentTimeMillis() - startTime;
            log.info("[{}] Encrypted authentication successful for client: {} ({}ms)", traceId, clientId, responseTime);

            return ResponseEntity.ok(ApiResponse.builder()
                    .success(true)
                    .statusCode("AUTH_SUCCESS")
                    .message("Authentication successful")
                    .data(encryptedResponse)
                    .traceId(traceId)
                    .httpStatusCode(200)
                    .build());

        } catch (Exception e) {
            long responseTime = System.currentTimeMillis() - startTime;
            log.error("[{}] Encrypted authentication error for client: {} ({}ms) - {}", traceId, clientId, responseTime, e.getMessage(), e);

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.builder()
                            .success(false)
                            .statusCode("AUTH_ERROR")
                            .message("Authentication failed")
                            .traceId(traceId)
                            .httpStatusCode(500)
                            .build());
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<?>> logout() {
        String clientId = (String) org.springframework.security.core.context.SecurityContextHolder
                .getContext().getAuthentication().getPrincipal();
        String traceId = UUID.randomUUID().toString();

        try {
            clientAuthService.invalidateSession(clientId);
            log.info("[{}] Logout successful for client: {}", traceId, clientId);

            return ResponseEntity.ok(ApiResponse.builder()
                    .success(true)
                    .statusCode("LOGOUT_SUCCESS")
                    .message("Logout successful")
                    .traceId(traceId)
                    .build());
        } catch (Exception e) {
            log.error("[{}] Logout error for client: {} - {}", traceId, clientId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.builder()
                            .success(false)
                            .statusCode("LOGOUT_ERROR")
                            .message("Logout failed")
                            .traceId(traceId)
                            .httpStatusCode(500)
                            .build());
        }
    }

    private PublicKey parsePublicKey(String publicKeyPem) throws Exception {
        String cleanKey = publicKeyPem
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s", "");
        
        byte[] decodedKey = Base64.getDecoder().decode(cleanKey);
        X509EncodedKeySpec keySpec = new X509EncodedKeySpec(decodedKey);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        return keyFactory.generatePublic(keySpec);
    }

    private PrivateKey parsePrivateKey(String privateKeyPem) throws Exception {
        String cleanKey = privateKeyPem
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replace("-----BEGIN RSA PRIVATE KEY-----", "")
                .replace("-----END RSA PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        
        byte[] decodedKey = Base64.getDecoder().decode(cleanKey);
        PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(decodedKey);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        return keyFactory.generatePrivate(keySpec);
    }
}
