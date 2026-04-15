package com.anju.appointment.audit.service;

import com.anju.appointment.audit.dto.AuditLogResponse;
import com.anju.appointment.audit.entity.AuditLog;
import com.anju.appointment.audit.repository.AuditLogRepository;
import com.anju.appointment.auth.security.AuthenticatedUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;

@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);
    private final AuditLogRepository auditLogRepository;

    public AuditService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    public void log(Long userId, String username, String module, String operation,
                    String entityType, Long entityId, String details, String ipAddress) {
        // Auto-resolve username from SecurityContext if not provided
        String resolvedUsername = username;
        String resolvedIp = ipAddress;
        if (resolvedUsername == null && userId != null) {
            resolvedUsername = resolveCurrentUsername();
        }
        if (resolvedIp == null) {
            resolvedIp = resolveCurrentIpAddress();
        }

        AuditLog auditLog = new AuditLog();
        auditLog.setUserId(userId);
        auditLog.setUsername(resolvedUsername);
        auditLog.setModule(module);
        auditLog.setOperation(operation);
        auditLog.setEntityType(entityType);
        auditLog.setEntityId(entityId);
        auditLog.setDetails(details);
        auditLog.setIpAddress(resolvedIp);

        auditLogRepository.save(auditLog);
        log.debug("Audit: {} {} {} {} id={}", resolvedUsername, operation, module, entityType, entityId);
    }

    public Page<AuditLogResponse> listLogs(Long userId, String module, String operation,
                                            String dateFrom, String dateTo, Pageable pageable) {
        LocalDateTime from = parseDate(dateFrom, true);
        LocalDateTime to = parseDate(dateTo, false);

        return auditLogRepository.findByFilters(userId, module, operation, from, to, pageable)
                .map(AuditLogResponse::fromEntity);
    }

    private String resolveCurrentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AuthenticatedUser user) {
            return user.getUsername();
        }
        return null;
    }

    private String resolveCurrentIpAddress() {
        try {
            ServletRequestAttributes attrs =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                return attrs.getRequest().getRemoteAddr();
            }
        } catch (Exception ignored) {
            // Non-web context (scheduler, tests)
        }
        return null;
    }

    private LocalDateTime parseDate(String dateStr, boolean startOfDay) {
        if (dateStr == null) {
            return null;
        }
        try {
            LocalDate date = LocalDate.parse(dateStr);
            return startOfDay ? date.atStartOfDay() : date.atTime(23, 59, 59);
        } catch (DateTimeParseException e) {
            throw new com.anju.appointment.common.BusinessRuleException(
                    "Invalid date format: " + dateStr + ". Expected yyyy-MM-dd");
        }
    }
}
