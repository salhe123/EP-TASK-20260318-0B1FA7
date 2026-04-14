package com.anju.appointment.financial.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class RefundRequest {

    @NotNull(message = "Original transaction ID must not be null")
    private Long originalTransactionId;

    @NotNull(message = "Amount must not be null")
    @DecimalMin(value = "0.01", message = "Amount must be greater than 0")
    private BigDecimal amount;

    @NotBlank(message = "Reason must not be blank")
    private String reason;

    @NotBlank(message = "Idempotency key must not be blank")
    private String idempotencyKey;
}
