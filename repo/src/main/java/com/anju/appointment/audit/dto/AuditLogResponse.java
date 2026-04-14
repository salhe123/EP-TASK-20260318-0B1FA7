package com.anju.appointment.audit.dto;

import com.anju.appointment.audit.entity.AuditLog;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class AuditLogResponse {

    private Long id;
    private Long userId;
    private String username;
    private String module;
    private String operation;
    private String entityType;
    private Long entityId;
    private String details;
    private String ipAddress;
    private LocalDateTime timestamp;

    public static AuditLogResponse fromEntity(AuditLog log) {
        return AuditLogResponse.builder()
                .id(log.getId())
                .userId(log.getUserId())
                .username(log.getUsername())
                .module(log.getModule())
                .operation(log.getOperation())
                .entityType(log.getEntityType())
                .entityId(log.getEntityId())
                .details(log.getDetails())
                .ipAddress(log.getIpAddress())
                .timestamp(log.getTimestamp())
                .build();
    }
}
