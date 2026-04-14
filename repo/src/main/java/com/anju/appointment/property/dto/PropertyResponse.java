package com.anju.appointment.property.dto;

import com.anju.appointment.property.entity.Property;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Builder
public class PropertyResponse {

    private Long id;
    private String name;
    private String type;
    private String address;
    private String description;
    private int capacity;
    private String status;
    private String complianceStatus;
    private String complianceNotes;
    private LocalDate complianceExpiresAt;
    private BigDecimal rentalPricePerSlot;
    private BigDecimal depositAmount;
    private Integer minBookingLeadHours;
    private Integer maxBookingLeadDays;
    private String rentalRules;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static PropertyResponse fromEntity(Property property) {
        return PropertyResponse.builder()
                .id(property.getId())
                .name(property.getName())
                .type(property.getType())
                .address(property.getAddress())
                .description(property.getDescription())
                .capacity(property.getCapacity())
                .status(property.getStatus().name())
                .complianceStatus(property.getComplianceStatus() != null
                        ? property.getComplianceStatus().name() : null)
                .complianceNotes(property.getComplianceNotes())
                .complianceExpiresAt(property.getComplianceExpiresAt())
                .rentalPricePerSlot(property.getRentalPricePerSlot())
                .depositAmount(property.getDepositAmount())
                .minBookingLeadHours(property.getMinBookingLeadHours())
                .maxBookingLeadDays(property.getMaxBookingLeadDays())
                .rentalRules(property.getRentalRules())
                .createdAt(property.getCreatedAt())
                .updatedAt(property.getUpdatedAt())
                .build();
    }
}
