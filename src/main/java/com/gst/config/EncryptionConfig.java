package com.gst.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;

@Configuration
@ConfigurationProperties(prefix = "app.encryption")
public class EncryptionConfig {

    private String algorithm = "RSA";
    private int keySize = 2048;
    private String cipherAlgorithm = "RSA/ECB/PKCS1Padding";

    public String getAlgorithm() {
        return algorithm;
    }

    public void setAlgorithm(String algorithm) {
        this.algorithm = algorithm;
    }

    public int getKeySize() {
        return keySize;
    }

    public void setKeySize(int keySize) {
        this.keySize = keySize;
    }

    public String getCipherAlgorithm() {
        return cipherAlgorithm;
    }

    public void setCipherAlgorithm(String cipherAlgorithm) {
        this.cipherAlgorithm = cipherAlgorithm;
    }

    public KeyPair generateKeyPair() throws NoSuchAlgorithmException {
        KeyPairGenerator generator = KeyPairGenerator.getInstance(algorithm);
        generator.initialize(keySize);
        return generator.generateKeyPair();
    }
}
