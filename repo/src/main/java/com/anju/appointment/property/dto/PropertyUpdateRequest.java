package com.anju.appointment.property.dto;

import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
public class PropertyUpdateRequest {

    private String name;

    private String type;

    private String address;

    private String description;

    @Min(value = 1, message = "Capacity must be at least 1")
    private Integer capacity;

    // Compliance fields
    private String complianceStatus;
    private String complianceNotes;
    private LocalDate complianceExpiresAt;

    // Rental rule fields
    private BigDecimal rentalPricePerSlot;
    private BigDecimal depositAmount;

    @Min(value = 1, message = "Minimum booking lead hours must be at least 1")
    private Integer minBookingLeadHours;

    @Min(value = 1, message = "Maximum booking lead days must be at least 1")
    private Integer maxBookingLeadDays;

    private String rentalRules;
}
