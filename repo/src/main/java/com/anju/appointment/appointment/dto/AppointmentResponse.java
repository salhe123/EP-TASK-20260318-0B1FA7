package com.anju.appointment.appointment.dto;

import com.anju.appointment.appointment.entity.Appointment;
import com.anju.appointment.common.DataMaskingUtil;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class AppointmentResponse {

    private Long id;
    private Long slotId;
    private Long propertyId;
    private Long userId;
    private String patientName;
    private String patientPhone;
    private String serviceType;
    private String status;
    private String notes;
    private int rescheduleCount;
    private String cancelReason;
    private String completionNotes;
    private String idempotencyKey;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime expiresAt;

    public static AppointmentResponse fromEntity(Appointment appointment) {
        return AppointmentResponse.builder()
                .id(appointment.getId())
                .slotId(appointment.getSlotId())
                .propertyId(appointment.getPropertyId())
                .userId(appointment.getUserId())
                .patientName(DataMaskingUtil.maskName(appointment.getPatientName()))
                .patientPhone(DataMaskingUtil.maskPhone(appointment.getPatientPhone()))
                .serviceType(appointment.getServiceType())
                .status(appointment.getStatus().name())
                .notes(appointment.getNotes())
                .rescheduleCount(appointment.getRescheduleCount())
                .cancelReason(appointment.getCancelReason())
                .completionNotes(appointment.getCompletionNotes())
                .idempotencyKey(appointment.getIdempotencyKey())
                .createdAt(appointment.getCreatedAt())
                .updatedAt(appointment.getUpdatedAt())
                .expiresAt(appointment.getExpiresAt())
                .build();
    }
}
