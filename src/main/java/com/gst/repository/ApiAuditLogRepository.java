package com.gst.repository;

import com.gst.entity.ApiAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ApiAuditLogRepository extends JpaRepository<ApiAuditLog, Long> {
    List<ApiAuditLog> findByClientIdAndCreatedAtBetween(String clientId, LocalDateTime startTime, LocalDateTime endTime);
    List<ApiAuditLog> findByClientId(String clientId);
}
