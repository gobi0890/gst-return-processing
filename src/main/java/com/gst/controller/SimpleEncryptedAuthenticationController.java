package com.gst.controller;

import com.gst.constants.ErrorCode;
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

import javax.validation.Valid;
import java.security.PrivateKey;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
public class SimpleEncryptedAuthenticationController {

    @Autowired
    private ClientAuthenticationService clientAuthService;

    @Autowired
    private EncryptionService encryptionService;

    @Autowired
    private JwtTokenProvider tokenProvider;

    @Autowired
    private ClientRepository clientRepository;

    /**
     * Simplified Encrypted Login Flow
     *
     * Request Headers:
     * - X-Client-ID: client-001
     * - X-Client-Secret: secret-123
     *
     * Request Body:
     * {
     *   "encryptedAppKey": "<RSA-ENCRYPTED-AES-KEY-BASE64>"
     * }
     *
     * Response:
     * {
     *   "auth_token": "<JWT_TOKEN>",
     *   "sek": "<BASE64_AES_KEY>",
     *   "token_type": "Bearer",
     *   "expires_in": 1800000
     * }
     */
    @PostMapping("/login/simple")
    public ResponseEntity<ApiResponse<?>> simpleEncryptedLogin(
            @RequestHeader(value = "X-Client-ID", required = true) String clientId,
            @RequestHeader(value = "X-Client-Secret", required = true) String clientSecret,
            @Valid @RequestBody SimpleEncryptedAuthRequest request) {

        String traceId = UUID.randomUUID().toString();
        long startTime = System.currentTimeMillis();

        try {
            log.info("[{}] Simple encrypted authentication attempt for client: {}", traceId, clientId);

            // Validate headers
            if (clientId == null || clientId.trim().isEmpty()) {
                log.warn("[{}] Missing X-Client-ID header", traceId);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(ApiResponse.builder()
                                .success(false)
                                .statusCode(ErrorCode.MISSING_CLIENT_ID.getCode())
                                .message(ErrorCode.MISSING_CLIENT_ID.getMessage())
                                .traceId(traceId)
                                .httpStatusCode(ErrorCode.MISSING_CLIENT_ID.getHttpStatus())
                                .build());
            }

            if (clientSecret == null || clientSecret.trim().isEmpty()) {
                log.warn("[{}] Missing X-Client-Secret header", traceId);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(ApiResponse.builder()
                                .success(false)
                                .statusCode(ErrorCode.MISSING_CLIENT_SECRET.getCode())
                                .message(ErrorCode.MISSING_CLIENT_SECRET.getMessage())
                                .traceId(traceId)
                                .httpStatusCode(ErrorCode.MISSING_CLIENT_SECRET.getHttpStatus())
                                .build());
            }

            // Step 1: Verify client exists and is active
            Optional<Client> clientOpt = clientRepository.findByClientIdAndIsActive(clientId, true);
            if (clientOpt.isEmpty()) {
                log.warn("[{}] Client not found or inactive: {}", traceId, clientId);
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(ApiResponse.builder()
                                .success(false)
                                .statusCode(ErrorCode.INVALID_CLIENT.getCode())
                                .message(ErrorCode.INVALID_CLIENT.getMessage())
                                .traceId(traceId)
                                .httpStatusCode(ErrorCode.INVALID_CLIENT.getHttpStatus())
                                .build());
            }

            Client client = clientOpt.get();
            log.debug("[{}] Client found: {}", traceId, clientId);

            // Step 2: Verify client credentials (SHA-512 hash)
            if (!clientAuthService.verifyClientCredentials(clientId, clientSecret)) {
                log.warn("[{}] Invalid credentials for client: {}", traceId, clientId);
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(ApiResponse.builder()
                                .success(false)
                                .statusCode(ErrorCode.INVALID_CREDENTIALS.getCode())
                                .message(ErrorCode.INVALID_CREDENTIALS.getMessage())
                                .traceId(traceId)
                                .httpStatusCode(ErrorCode.INVALID_CREDENTIALS.getHttpStatus())
                                .build());
            }

            log.debug("[{}] Client credentials verified", traceId);

            // Step 3: Decrypt encryptedAppKey using server's RSA private key
            byte[] appKeyBytes;
            try {
                PrivateKey serverPrivateKey = encryptionService.loadPrivateKeyFromPem(client.getRsaPrivateKeyPem());
                appKeyBytes = encryptionService.decryptAppKeyWithRSA(request.getEncryptedAppKey(), serverPrivateKey);
                log.debug("[{}] App key decrypted successfully", traceId);
            } catch (Exception e) {
                log.error("[{}] Failed to decrypt app key: {}", traceId, e.getMessage());
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(ApiResponse.builder()
                                .success(false)
                                .statusCode(ErrorCode.DECRYPTION_FAILED.getCode())
                                .message(ErrorCode.DECRYPTION_FAILED.getMessage())
                                .traceId(traceId)
                                .httpStatusCode(ErrorCode.DECRYPTION_FAILED.getHttpStatus())
                                .build());
            }

            // Step 4: Generate JWT Auth Token (30 minutes)
            String authToken = tokenProvider.generateToken(clientId, clientSecret);
            log.debug("[{}] Generated JWT token for client: {}", traceId, clientId);

            // Step 5: Generate SEK (Session Encryption Key) - Base64 AES-256 key
            String sek = encryptionService.generateSEK();
            log.debug("[{}] Generated SEK for client: {}", traceId, clientId);

            // Step 6: Create client session
            ClientSession session = clientAuthService.createClientSession(clientId, sek);
            log.info("[{}] Client session created for: {}", traceId, clientId);

            // Step 7: Prepare response
            SimpleAuthResponse authResponse = SimpleAuthResponse.builder()
                    .authToken(authToken)
                    .sek(sek)
                    .tokenType("Bearer")
                    .expiresIn(tokenProvider.getExpirationTime())
                    .build();

            long responseTime = System.currentTimeMillis() - startTime;
            log.info("[{}] Simple encrypted authentication successful for client: {} ({}ms)", traceId, clientId, responseTime);

            return ResponseEntity.ok(ApiResponse.builder()
                    .success(true)
                    .statusCode("AUTH_SUCCESS")
                    .message("Authentication successful")
                    .data(authResponse)
                    .traceId(traceId)
                    .httpStatusCode(200)
                    .build());

        } catch (Exception e) {
            long responseTime = System.currentTimeMillis() - startTime;
            log.error("[{}] Simple encrypted authentication error for client: {} ({}ms) - {}", traceId, clientId, responseTime, e.getMessage(), e);

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.builder()
                            .success(false)
                            .statusCode(ErrorCode.INTERNAL_SERVER_ERROR.getCode())
                            .message(ErrorCode.INTERNAL_SERVER_ERROR.getMessage())
                            .traceId(traceId)
                            .httpStatusCode(ErrorCode.INTERNAL_SERVER_ERROR.getHttpStatus())
                            .build());
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<?>> logout(
            @RequestHeader("Authorization") String bearerToken) {

        String traceId = UUID.randomUUID().toString();
        long startTime = System.currentTimeMillis();

        try {
            String token = bearerToken.startsWith("Bearer ") ? bearerToken.substring(7) : bearerToken;
            String clientId = tokenProvider.getClientIdFromToken(token);

            if (clientId == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(ApiResponse.builder()
                                .success(false)
                                .statusCode(ErrorCode.INVALID_TOKEN.getCode())
                                .message(ErrorCode.INVALID_TOKEN.getMessage())
                                .traceId(traceId)
                                .httpStatusCode(ErrorCode.INVALID_TOKEN.getHttpStatus())
                                .build());
            }

            clientAuthService.invalidateSession(clientId);
            log.info("[{}] Logout successful for client: {}", traceId, clientId);

            long responseTime = System.currentTimeMillis() - startTime;
            return ResponseEntity.ok(ApiResponse.builder()
                    .success(true)
                    .statusCode("LOGOUT_SUCCESS")
                    .message("Logout successful")
                    .traceId(traceId)
                    .build());
        } catch (Exception e) {
            log.error("[{}] Logout error - {}", traceId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.builder()
                            .success(false)
                            .statusCode(ErrorCode.LOGOUT_FAILED.getCode())
                            .message(ErrorCode.LOGOUT_FAILED.getMessage())
                            .traceId(traceId)
                            .httpStatusCode(ErrorCode.LOGOUT_FAILED.getHttpStatus())
                            .build());
        }
    }
}
