package com.anju.appointment.audit.repository;

import com.anju.appointment.audit.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    @Query("SELECT a FROM AuditLog a WHERE " +
           "(:userId IS NULL OR a.userId = :userId) AND " +
           "(:module IS NULL OR a.module = :module) AND " +
           "(:operation IS NULL OR a.operation = :operation) AND " +
           "(:dateFrom IS NULL OR a.timestamp >= :dateFrom) AND " +
           "(:dateTo IS NULL OR a.timestamp <= :dateTo)")
    Page<AuditLog> findByFilters(@Param("userId") Long userId,
                                  @Param("module") String module,
                                  @Param("operation") String operation,
                                  @Param("dateFrom") LocalDateTime dateFrom,
                                  @Param("dateTo") LocalDateTime dateTo,
                                  Pageable pageable);
}
