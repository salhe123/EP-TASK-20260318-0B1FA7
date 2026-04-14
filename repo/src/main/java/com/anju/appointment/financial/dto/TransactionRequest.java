package com.anju.appointment.financial.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class TransactionRequest {

    @NotNull(message = "Appointment ID must not be null")
    private Long appointmentId;

    @NotBlank(message = "Type must not be blank")
    private String type;

    @NotNull(message = "Amount must not be null")
    @DecimalMin(value = "0.01", message = "Amount must be greater than 0")
    private BigDecimal amount;

    private String currency;

    private String description;

    @NotBlank(message = "Idempotency key must not be blank")
    private String idempotencyKey;
}
