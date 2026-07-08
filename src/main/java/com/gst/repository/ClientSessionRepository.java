package com.gst.repository;

import com.gst.entity.ClientSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface ClientSessionRepository extends JpaRepository<ClientSession, Long> {
    Optional<ClientSession> findByAuthToken(String authToken);
    
    @Query("SELECT cs FROM ClientSession cs WHERE cs.client.clientId = :clientId AND cs.isActive = true AND cs.expiryTime > :now")
    Optional<ClientSession> findActiveSessionByClientId(@Param("clientId") String clientId, @Param("now") LocalDateTime now);
    
    default Optional<ClientSession> findActiveSessionByClientId(String clientId) {
        return findActiveSessionByClientId(clientId, LocalDateTime.now());
    }
}
