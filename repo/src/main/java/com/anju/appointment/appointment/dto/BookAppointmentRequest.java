package com.anju.appointment.appointment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class BookAppointmentRequest {

    @NotNull(message = "Slot ID must not be null")
    private Long slotId;

    @NotBlank(message = "Patient name must not be blank")
    private String patientName;

    @NotBlank(message = "Patient phone must not be blank")
    private String patientPhone;

    @NotBlank(message = "Service type must not be blank")
    private String serviceType;

    private String notes;

    @NotBlank(message = "Idempotency key must not be blank")
    private String idempotencyKey;
}
