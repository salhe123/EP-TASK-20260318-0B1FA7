package com.anju.appointment.appointment.repository;

import com.anju.appointment.appointment.entity.AppointmentSlot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface AppointmentSlotRepository extends JpaRepository<AppointmentSlot, Long> {

    List<AppointmentSlot> findByPropertyIdAndDate(Long propertyId, LocalDate date);

    boolean existsByPropertyIdAndDate(Long propertyId, LocalDate date);
}
