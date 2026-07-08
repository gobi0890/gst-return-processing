package com.gst.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Base64;

@Slf4j
@Service
public class EncryptionService {

    private static final String CIPHER_ALGORITHM = "RSA/ECB/PKCS1Padding";
    private static final String KEY_ALGORITHM = "RSA";

    /**
     * Encrypt data using public key
     */
    public String encryptWithPublicKey(String data, PublicKey publicKey) {
        try {
            Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, publicKey);
            byte[] encryptedData = cipher.doFinal(data.getBytes());
            return Base64.getEncoder().encodeToString(encryptedData);
        } catch (Exception e) {
            log.error("Error encrypting data with public key: {}", e.getMessage(), e);
            throw new RuntimeException("Encryption failed", e);
        }
    }

    /**
     * Decrypt data using private key
     */
    public String decryptWithPrivateKey(String encryptedData, PrivateKey privateKey) {
        try {
            Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, privateKey);
            byte[] decodedData = Base64.getDecoder().decode(encryptedData);
            byte[] decryptedData = cipher.doFinal(decodedData);
            return new String(decryptedData);
        } catch (Exception e) {
            log.error("Error decrypting data with private key: {}", e.getMessage(), e);
            throw new RuntimeException("Decryption failed", e);
        }
    }

    /**
     * Encrypt data using SEK (Session Encryption Key) - AES
     */
    public String encryptWithSEK(String data, String sek) {
        try {
            // For production, use AES encryption
            Cipher cipher = Cipher.getInstance("AES");
            // This is a simplified version - implement proper AES encryption with IV
            byte[] encryptedData = data.getBytes(); // Replace with actual AES encryption
            return Base64.getEncoder().encodeToString(encryptedData);
        } catch (Exception e) {
            log.error("Error encrypting data with SEK: {}", e.getMessage(), e);
            throw new RuntimeException("SEK encryption failed", e);
        }
    }

    /**
     * Decrypt data using SEK (Session Encryption Key) - AES
     */
    public String decryptWithSEK(String encryptedData, String sek) {
        try {
            // For production, use AES decryption
            byte[] decodedData = Base64.getDecoder().decode(encryptedData);
            return new String(decodedData); // Replace with actual AES decryption
        } catch (Exception e) {
            log.error("Error decrypting data with SEK: {}", e.getMessage(), e);
            throw new RuntimeException("SEK decryption failed", e);
        }
    }
}
