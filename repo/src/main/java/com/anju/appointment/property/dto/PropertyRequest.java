package com.anju.appointment.property.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
public class PropertyRequest {

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
    @DecimalMin(value = "0.00", inclusive = true, message = "Rental price per slot must be greater than or equal to 0")
    private BigDecimal rentalPricePerSlot;

    @DecimalMin(value = "0.00", inclusive = true, message = "Deposit amount must be greater than or equal to 0")
    private BigDecimal depositAmount;

    @Min(value = 0, message = "Minimum booking lead hours must be greater than or equal to 0")
    private Integer minBookingLeadHours;

    @Min(value = 0, message = "Maximum booking lead days must be greater than or equal to 0")
    private Integer maxBookingLeadDays;

    private String rentalRules;
}
