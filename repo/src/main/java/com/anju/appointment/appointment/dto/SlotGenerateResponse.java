package com.anju.appointment.appointment.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;

@Getter
@Builder
public class SlotGenerateResponse {

    private Long propertyId;
    private LocalDate date;
    private int slotsGenerated;
    private int slotDuration;
}
