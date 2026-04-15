package com.anju.appointment.appointment.repository;

import com.anju.appointment.appointment.entity.Appointment;
import com.anju.appointment.appointment.entity.AppointmentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

public interface AppointmentRepository extends JpaRepository<Appointment, Long> {

    Optional<Appointment> findByIdempotencyKey(String idempotencyKey);

    boolean existsByIdAndUserId(Long id, Long userId);

    List<Appointment> findByStatusAndExpiresAtBefore(AppointmentStatus status, LocalDateTime expiresAt);

    boolean existsByPropertyIdAndStatusIn(Long propertyId, List<AppointmentStatus> statuses);

    @Query("SELECT a FROM Appointment a JOIN AppointmentSlot s ON a.slotId = s.id " +
           "WHERE a.userId = :userId " +
           "AND a.status IN :activeStatuses " +
           "AND s.date = :date " +
           "AND s.startTime < :endTime " +
           "AND s.endTime > :startTime " +
           "AND (:excludeAppointmentId IS NULL OR a.id <> :excludeAppointmentId)")
    List<Appointment> findOverlapping(@Param("userId") Long userId,
                                       @Param("date") LocalDate date,
                                       @Param("startTime") LocalTime startTime,
                                       @Param("endTime") LocalTime endTime,
                                       @Param("activeStatuses") List<AppointmentStatus> activeStatuses,
                                       @Param("excludeAppointmentId") Long excludeAppointmentId);

    @Query("SELECT a FROM Appointment a JOIN AppointmentSlot s ON a.slotId = s.id WHERE " +
           "(:propertyId IS NULL OR a.propertyId = :propertyId) AND " +
           "(:status IS NULL OR a.status = :status) AND " +
           "(:patientName IS NULL OR LOWER(a.patientName) LIKE LOWER(CONCAT('%', :patientName, '%'))) AND " +
           "(:dateFrom IS NULL OR CAST(s.date AS timestamp) >= :dateFrom) AND " +
           "(:dateTo IS NULL OR CAST(s.date AS timestamp) <= :dateTo)")
    Page<Appointment> findByFilters(@Param("propertyId") Long propertyId,
                                     @Param("status") AppointmentStatus status,
                                     @Param("patientName") String patientName,
                                     @Param("dateFrom") LocalDateTime dateFrom,
                                     @Param("dateTo") LocalDateTime dateTo,
                                     Pageable pageable);

    @Query("SELECT a FROM Appointment a JOIN AppointmentSlot s ON a.slotId = s.id WHERE a.userId = :userId AND " +
           "(:propertyId IS NULL OR a.propertyId = :propertyId) AND " +
           "(:status IS NULL OR a.status = :status) AND " +
           "(:patientName IS NULL OR LOWER(a.patientName) LIKE LOWER(CONCAT('%', :patientName, '%'))) AND " +
           "(:dateFrom IS NULL OR CAST(s.date AS timestamp) >= :dateFrom) AND " +
           "(:dateTo IS NULL OR CAST(s.date AS timestamp) <= :dateTo)")
    Page<Appointment> findByUserIdAndFilters(@Param("userId") Long userId,
                                              @Param("propertyId") Long propertyId,
                                              @Param("status") AppointmentStatus status,
                                              @Param("patientName") String patientName,
                                              @Param("dateFrom") LocalDateTime dateFrom,
                                              @Param("dateTo") LocalDateTime dateTo,
                                              Pageable pageable);

    @Query("SELECT a FROM Appointment a JOIN AppointmentSlot s ON a.slotId = s.id WHERE a.assignedServiceStaffId = :staffId AND " +
           "(:propertyId IS NULL OR a.propertyId = :propertyId) AND " +
           "(:status IS NULL OR a.status = :status) AND " +
           "(:patientName IS NULL OR LOWER(a.patientName) LIKE LOWER(CONCAT('%', :patientName, '%'))) AND " +
           "(:dateFrom IS NULL OR CAST(s.date AS timestamp) >= :dateFrom) AND " +
           "(:dateTo IS NULL OR CAST(s.date AS timestamp) <= :dateTo)")
    Page<Appointment> findByAssignedStaffAndFilters(@Param("staffId") Long staffId,
                                                     @Param("propertyId") Long propertyId,
                                                     @Param("status") AppointmentStatus status,
                                                     @Param("patientName") String patientName,
                                                     @Param("dateFrom") LocalDateTime dateFrom,
                                                     @Param("dateTo") LocalDateTime dateTo,
                                                     Pageable pageable);
}
