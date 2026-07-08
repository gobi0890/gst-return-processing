package com.gst.security;

import org.apache.commons.codec.binary.Base64;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Security;
import java.security.spec.PKCS8EncodedKeySpec;

/**
 * Server-side encryption service for RSA and AES operations
 */
@Service
public class EncryptionService {

    static {
        Security.addProvider(new BouncyCastleProvider());
    }

    private static final String RSA_ALGORITHM = "RSA/ECB/PKCS1Padding";
    private static final String AES_ALGORITHM = "AES/ECB/PKCS5Padding";
    private static final String KEY_ALGORITHM = "RSA";
    private static final int AES_KEY_SIZE = 256;

    /**
     * Generate a random 256-bit AES key (SEK - Session Encryption Key)
     */
    public String generateSEK() {
        try {
            KeyGenerator keyGen = KeyGenerator.getInstance("AES");
            keyGen.init(AES_KEY_SIZE);
            SecretKey secretKey = keyGen.generateKey();
            return Base64.encodeBase64String(secretKey.getEncoded());
        } catch (Exception e) {
            throw new RuntimeException("Error generating SEK", e);
        }
    }

    /**
     * Decrypt appKey using RSA private key
     */
    public byte[] decryptAppKeyWithRSA(String encryptedAppKey, PrivateKey privateKey) {
        try {
            byte[] appKeyBytes = Base64.decodeBase64(encryptedAppKey);
            Cipher cipher = Cipher.getInstance(RSA_ALGORITHM, "BC");
            cipher.init(Cipher.DECRYPT_MODE, privateKey);
            return cipher.doFinal(appKeyBytes);
        } catch (Exception e) {
            throw new RuntimeException("Error decrypting app key with RSA", e);
        }
    }

    /**
     * Decrypt data using AES-256 ECB mode with PKCS5 padding
     */
    public String decryptWithAES(String encryptedText, byte[] aesKey) {
        try {
            SecretKey secretKey = new SecretKeySpec(aesKey, 0, aesKey.length, "AES");
            Cipher cipher = Cipher.getInstance(AES_ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, secretKey);
            byte[] decodedEncryptedText = Base64.decodeBase64(encryptedText);
            byte[] decryptedData = cipher.doFinal(decodedEncryptedText);
            return new String(decryptedData, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("Error decrypting with AES", e);
        }
    }

    /**
     * Encrypt data using AES-256 ECB mode with PKCS5 padding
     */
    public String encryptWithAES(String plainText, byte[] aesKey) {
        try {
            SecretKey secretKey = new SecretKeySpec(aesKey, 0, aesKey.length, "AES");
            Cipher cipher = Cipher.getInstance(AES_ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey);
            byte[] encryptedData = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            return Base64.encodeBase64String(encryptedData);
        } catch (Exception e) {
            throw new RuntimeException("Error encrypting with AES", e);
        }
    }

    /**
     * Load RSA private key from PEM format
     */
    public PrivateKey loadPrivateKeyFromPem(String privateKeyPem) {
        try {
            String key = privateKeyPem
                    .replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----BEGIN RSA PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "")
                    .replace("-----END RSA PRIVATE KEY-----", "")
                    .replaceAll("\\s+", "");

            byte[] decodedKey = Base64.decodeBase64(key);
            PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(decodedKey);
            KeyFactory keyFactory = KeyFactory.getInstance(KEY_ALGORITHM);
            return keyFactory.generatePrivate(spec);
        } catch (Exception e) {
            throw new RuntimeException("Error loading private key from PEM", e);
        }
    }

    /**
     * Generate HMAC-SHA256
     */
    public String generateHmac(String data, byte[] key) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        SecretKeySpec secretKey = new SecretKeySpec(key, "HmacSHA256");
        mac.init(secretKey);
        byte[] hmac = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        return Base64.encodeBase64String(hmac);
    }
}
