package com.anju.appointment.appointment.entity;

import com.anju.appointment.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "appointments", indexes = {
        @Index(name = "idx_appointment_idempotency", columnList = "idempotencyKey", unique = true),
        @Index(name = "idx_appointment_slot_status", columnList = "slotId, status"),
        @Index(name = "idx_appointment_user", columnList = "userId"),
        @Index(name = "idx_appointment_property", columnList = "propertyId")
})
@Getter
@Setter
@NoArgsConstructor
public class Appointment extends BaseEntity {

    @Column(nullable = false)
    private Long slotId;

    @Column(nullable = false)
    private Long propertyId;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private String patientName;

    @Column(nullable = false)
    private String patientPhone;

    @Column(nullable = false)
    private String serviceType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AppointmentStatus status = AppointmentStatus.CREATED;

    private String notes;

    @Column(nullable = false)
    private int rescheduleCount = 0;

    private String cancelReason;

    private String completionNotes;

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    @Column(nullable = false, unique = true)
    private String idempotencyKey;

    private Long assignedServiceStaffId;
}
