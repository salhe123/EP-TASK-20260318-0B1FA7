package com.anju.appointment.property.entity;

import com.anju.appointment.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "properties")
@Getter
@Setter
@NoArgsConstructor
public class Property extends BaseEntity {

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String type;

    private String address;

    private String description;

    @Column(nullable = false)
    private int capacity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PropertyStatus status = PropertyStatus.ACTIVE;

    // Compliance fields
    @Enumerated(EnumType.STRING)
    private ComplianceStatus complianceStatus;

    @Column(columnDefinition = "TEXT")
    private String complianceNotes;

    private LocalDate complianceExpiresAt;

    // Rental rule fields
    @Column(precision = 12, scale = 2)
    private BigDecimal rentalPricePerSlot;

    @Column(precision = 12, scale = 2)
    private BigDecimal depositAmount;

    private Integer minBookingLeadHours;

    private Integer maxBookingLeadDays;

    @Column(columnDefinition = "TEXT")
    private String rentalRules;
}
