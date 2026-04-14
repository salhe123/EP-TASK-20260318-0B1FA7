package com.anju.appointment.property.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
public class PropertyRequest {

    @NotBlank(message = "Name must not be blank")
    private String name;

    @NotBlank(message = "Type must not be blank")
    private String type;

    private String address;

    private String description;

    @NotNull(message = "Capacity must not be null")
    @Min(value = 1, message = "Capacity must be at least 1")
    private Integer capacity;

    // Compliance fields
    private String complianceStatus;
    private String complianceNotes;
    private LocalDate complianceExpiresAt;

    // Rental rule fields
    private BigDecimal rentalPricePerSlot;
    private BigDecimal depositAmount;
    private Integer minBookingLeadHours;
    private Integer maxBookingLeadDays;
    private String rentalRules;
}
