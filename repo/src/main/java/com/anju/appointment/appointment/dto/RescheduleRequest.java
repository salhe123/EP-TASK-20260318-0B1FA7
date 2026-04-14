package com.anju.appointment.appointment.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RescheduleRequest {

    @NotNull(message = "New slot ID must not be null")
    private Long newSlotId;

    private String reason;
}
