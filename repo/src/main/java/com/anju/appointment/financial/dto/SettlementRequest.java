package com.anju.appointment.financial.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class SettlementRequest {

    @NotNull(message = "Period start date must not be null")
    private LocalDate periodStart;

    @NotNull(message = "Period end date must not be null")
    private LocalDate periodEnd;

    private String notes;
}
