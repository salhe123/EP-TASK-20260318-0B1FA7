package com.anju.appointment.audit.service;

import com.anju.appointment.audit.dto.AuditLogResponse;
import com.anju.appointment.audit.entity.AuditLog;
import com.anju.appointment.audit.repository.AuditLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);
    private final AuditLogRepository auditLogRepository;

    public AuditService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    public void log(Long userId, String username, String module, String operation,
                    String entityType, Long entityId, String details, String ipAddress) {
        AuditLog auditLog = new AuditLog();
        auditLog.setUserId(userId);
        auditLog.setUsername(username);
        auditLog.setModule(module);
        auditLog.setOperation(operation);
        auditLog.setEntityType(entityType);
        auditLog.setEntityId(entityId);
        auditLog.setDetails(details);
        auditLog.setIpAddress(ipAddress);

        auditLogRepository.save(auditLog);
        log.debug("Audit: {} {} {} {} id={}", username, operation, module, entityType, entityId);
    }

    public Page<AuditLogResponse> listLogs(Long userId, String module, String operation,
                                            String dateFrom, String dateTo, Pageable pageable) {
        LocalDateTime from = dateFrom != null ? LocalDate.parse(dateFrom).atStartOfDay() : null;
        LocalDateTime to = dateTo != null ? LocalDate.parse(dateTo).atTime(23, 59, 59) : null;

        return auditLogRepository.findByFilters(userId, module, operation, from, to, pageable)
                .map(AuditLogResponse::fromEntity);
    }
}
