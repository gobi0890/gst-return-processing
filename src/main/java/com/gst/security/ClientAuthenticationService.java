package com.gst.security;

import com.gst.entity.Client;
import com.gst.entity.ClientSession;
import com.gst.repository.ClientRepository;
import com.gst.repository.ClientSessionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.Optional;

@Slf4j
@Service
public class ClientAuthenticationService {

    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private ClientSessionRepository clientSessionRepository;

    @Autowired
    private JwtTokenProvider tokenProvider;

    /**
     * Verify client credentials using SHA-512 hash
     */
    public boolean verifyClientCredentials(String clientId, String clientSecret) {
        Optional<Client> clientOpt = clientRepository.findByClientId(clientId);
        
        if (clientOpt.isEmpty()) {
            log.warn("Client not found: {}", clientId);
            return false;
        }

        Client client = clientOpt.get();
        String hashedSecret = hashSHA512(clientSecret);
        
        boolean isValid = client.getClientSecretHash().equals(hashedSecret);
        
        if (!isValid) {
            log.warn("Invalid client secret for client: {}", clientId);
        }
        
        return isValid;
    }

    /**
     * Generate SHA-512 hash of a string
     */
    public String hashSHA512(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-512");
            byte[] hashBytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            
            for (byte hashByte : hashBytes) {
                String hex = Integer.toHexString(0xff & hashByte);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            log.error("SHA-512 algorithm not found: {}", e.getMessage(), e);
            throw new RuntimeException("Hashing failed", e);
        }
    }

    /**
     * Create a new client session with JWT token and SEK
     */
    public ClientSession createClientSession(String clientId, String sek) {
        Optional<Client> clientOpt = clientRepository.findByClientId(clientId);
        
        if (clientOpt.isEmpty()) {
            throw new RuntimeException("Client not found: " + clientId);
        }

        Client client = clientOpt.get();
        String token = tokenProvider.generateToken(clientId, "");
        LocalDateTime expiryTime = LocalDateTime.now().plusMinutes(30);

        ClientSession session = ClientSession.builder()
                .client(client)
                .authToken(token)
                .sek(sek)
                .expiryTime(expiryTime)
                .isActive(true)
                .createdAt(LocalDateTime.now())
                .build();

        return clientSessionRepository.save(session);
    }

    /**
     * Retrieve active session for a client
     */
    public Optional<ClientSession> getActiveSession(String clientId) {
        return clientSessionRepository.findActiveSessionByClientId(clientId);
    }

    /**
     * Invalidate a client session
     */
    public void invalidateSession(String clientId) {
        Optional<ClientSession> sessionOpt = clientSessionRepository.findActiveSessionByClientId(clientId);
        
        if (sessionOpt.isPresent()) {
            ClientSession session = sessionOpt.get();
            session.setIsActive(false);
            clientSessionRepository.save(session);
            log.info("Session invalidated for client: {}", clientId);
        }
    }
}
