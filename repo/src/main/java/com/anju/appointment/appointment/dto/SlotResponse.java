package com.anju.appointment.appointment.dto;

import com.anju.appointment.appointment.entity.AppointmentSlot;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalTime;

@Getter
@Builder
public class SlotResponse {

    private Long id;
    private Long propertyId;
    private LocalDate date;
    private LocalTime startTime;
    private LocalTime endTime;
    private int duration;
    private int capacity;
    private int booked;
    private boolean available;

    public static SlotResponse fromEntity(AppointmentSlot slot) {
        return SlotResponse.builder()
                .id(slot.getId())
                .propertyId(slot.getPropertyId())
                .date(slot.getDate())
                .startTime(slot.getStartTime())
                .endTime(slot.getEndTime())
                .duration(slot.getDuration())
                .capacity(slot.getCapacity())
                .booked(slot.getBookedCount())
                .available(slot.getBookedCount() < slot.getCapacity())
                .build();
    }
}
