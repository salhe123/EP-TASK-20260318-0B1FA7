package com.anju.appointment.appointment.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalTime;

@Getter
@Setter
public class SlotGenerateRequest {

    @NotNull(message = "Property ID must not be null")
    private Long propertyId;

    @NotNull(message = "Date must not be null")
    private LocalDate date;

    @NotNull(message = "Slot duration must not be null")
    private Integer slotDuration;

    @NotNull(message = "Start time must not be null")
    private LocalTime startTime;

    @NotNull(message = "End time must not be null")
    private LocalTime endTime;

    private Integer capacity;
}
