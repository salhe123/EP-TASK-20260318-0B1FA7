package com.anju.appointment.appointment.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalTime;

@Entity
@Table(name = "appointment_slots", indexes = {
        @Index(name = "idx_slot_property_date", columnList = "propertyId, date")
})
@Getter
@Setter
@NoArgsConstructor
public class AppointmentSlot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long propertyId;

    @Column(nullable = false)
    private LocalDate date;

    @Column(nullable = false, columnDefinition = "TIME")
    private LocalTime startTime;

    @Column(nullable = false, columnDefinition = "TIME")
    private LocalTime endTime;

    @Column(nullable = false)
    private int duration;

    @Column(nullable = false)
    private int capacity;

    @Column(nullable = false)
    private int bookedCount = 0;

    @Version
    private Long version;
}
