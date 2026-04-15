package com.anju.appointment.appointment.service;

import com.anju.appointment.appointment.dto.AppointmentResponse;
import com.anju.appointment.appointment.dto.BookAppointmentRequest;
import com.anju.appointment.appointment.dto.SlotGenerateRequest;
import com.anju.appointment.appointment.dto.SlotGenerateResponse;
import com.anju.appointment.appointment.dto.SlotResponse;
import com.anju.appointment.appointment.entity.Appointment;
import com.anju.appointment.appointment.entity.AppointmentSlot;
import com.anju.appointment.appointment.entity.AppointmentStatus;
import com.anju.appointment.appointment.repository.AppointmentRepository;
import com.anju.appointment.appointment.repository.AppointmentSlotRepository;
import com.anju.appointment.audit.service.AuditService;
import com.anju.appointment.auth.entity.Role;
import com.anju.appointment.auth.entity.User;
import com.anju.appointment.auth.repository.UserRepository;
import com.anju.appointment.common.AuthorizationException;
import com.anju.appointment.common.BusinessRuleException;
import com.anju.appointment.common.ResourceNotFoundException;
import com.anju.appointment.property.entity.Property;
import com.anju.appointment.property.service.PropertyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
public class AppointmentService {

    private static final Logger log = LoggerFactory.getLogger(AppointmentService.class);
    private static final Set<Integer> ALLOWED_DURATIONS = Set.of(15, 30, 60, 90);
    private static final int DEFAULT_ADVANCE_BOOKING_HOURS = 2;
    private static final int DEFAULT_MAX_BOOKING_DAYS_AHEAD = 14;
    private static final int EXPIRY_MINUTES = 15;
    private static final int DEFAULT_MAX_RESCHEDULES = 2;
    private static final int LATE_CANCEL_THRESHOLD_MINUTES = 60;

    private final AppointmentSlotRepository slotRepository;
    private final AppointmentRepository appointmentRepository;
    private final PropertyService propertyService;
    private final AuditService auditService;
    private final UserRepository userRepository;

    public AppointmentService(AppointmentSlotRepository slotRepository,
                              AppointmentRepository appointmentRepository,
                              PropertyService propertyService,
                              AuditService auditService,
                              UserRepository userRepository) {
        this.slotRepository = slotRepository;
        this.appointmentRepository = appointmentRepository;
        this.propertyService = propertyService;
        this.auditService = auditService;
        this.userRepository = userRepository;
    }

    @Transactional
    public SlotGenerateResponse generateSlots(SlotGenerateRequest request) {
        if (!ALLOWED_DURATIONS.contains(request.getSlotDuration())) {
            throw new BusinessRuleException("Slot duration must be one of: 15, 30, 60, 90 minutes");
        }

        if (request.getStartTime().compareTo(request.getEndTime()) >= 0) {
            throw new BusinessRuleException("Start time must be before end time");
        }

        Property property = propertyService.findPropertyOrThrow(request.getPropertyId());

        if (slotRepository.existsByPropertyIdAndDate(request.getPropertyId(), request.getDate())) {
            throw new BusinessRuleException("Slots already exist for this property and date");
        }

        int capacity = request.getCapacity() != null ? request.getCapacity() : property.getCapacity();

        List<AppointmentSlot> slots = new ArrayList<>();
        LocalTime current = request.getStartTime();
        while (current.plusMinutes(request.getSlotDuration()).compareTo(request.getEndTime()) <= 0) {
            AppointmentSlot slot = new AppointmentSlot();
            slot.setPropertyId(request.getPropertyId());
            slot.setDate(request.getDate());
            slot.setStartTime(current);
            slot.setEndTime(current.plusMinutes(request.getSlotDuration()));
            slot.setDuration(request.getSlotDuration());
            slot.setCapacity(capacity);
            slots.add(slot);
            current = current.plusMinutes(request.getSlotDuration());
        }

        slotRepository.saveAll(slots);

        return SlotGenerateResponse.builder()
                .propertyId(request.getPropertyId())
                .date(request.getDate())
                .slotsGenerated(slots.size())
                .slotDuration(request.getSlotDuration())
                .build();
    }

    public List<SlotResponse> listSlots(Long propertyId, LocalDate date) {
        return slotRepository.findByPropertyIdAndDate(propertyId, date).stream()
                .map(SlotResponse::fromEntity)
                .toList();
    }

    @Transactional
    public AppointmentResponse bookAppointment(BookAppointmentRequest request, Long userId) {
        Optional<Appointment> existing = appointmentRepository.findByIdempotencyKey(request.getIdempotencyKey());
        if (existing.isPresent()) {
            if (!existing.get().getUserId().equals(userId)) {
                throw new BusinessRuleException("Idempotency key already used");
            }
            return AppointmentResponse.fromEntity(existing.get());
        }

        AppointmentSlot slot = slotRepository.findById(request.getSlotId())
                .orElseThrow(() -> new ResourceNotFoundException("Slot not found with id: " + request.getSlotId()));

        // Derive propertyId from the slot — never trust client-supplied propertyId
        Long propertyId = slot.getPropertyId();

        Property property = propertyService.findPropertyOrThrow(propertyId);
        propertyService.validateBookingEligibility(property);

        int advanceHours = resolveAdvanceBookingHours(property);
        int maxDaysAhead = resolveMaxBookingDaysAhead(property);

        LocalDateTime appointmentTime = LocalDateTime.of(slot.getDate(), slot.getStartTime());
        LocalDateTime now = LocalDateTime.now();

        if (appointmentTime.isBefore(now.plusHours(advanceHours))) {
            throw new BusinessRuleException("Appointment must be at least " + advanceHours + " hours in the future");
        }

        if (slot.getDate().isAfter(LocalDate.now().plusDays(maxDaysAhead))) {
            throw new BusinessRuleException("Cannot book more than " + maxDaysAhead + " days ahead");
        }

        if (slot.getBookedCount() >= slot.getCapacity()) {
            throw new BusinessRuleException("Slot is at full capacity");
        }

        List<Appointment> overlapping = appointmentRepository.findOverlapping(
                userId, slot.getDate(), slot.getStartTime(), slot.getEndTime(),
                List.of(AppointmentStatus.CREATED, AppointmentStatus.CONFIRMED), null);
        if (!overlapping.isEmpty()) {
            throw new BusinessRuleException("User already has an overlapping appointment during this time");
        }

        try {
            slot.setBookedCount(slot.getBookedCount() + 1);
            slotRepository.save(slot);
        } catch (ObjectOptimisticLockingFailureException e) {
            throw new BusinessRuleException("Slot booking conflict. Please try again");
        }

        Appointment appointment = new Appointment();
        appointment.setSlotId(request.getSlotId());
        appointment.setPropertyId(propertyId);
        appointment.setUserId(userId);
        appointment.setPatientName(request.getPatientName());
        appointment.setPatientPhone(request.getPatientPhone());
        appointment.setServiceType(request.getServiceType());
        appointment.setNotes(request.getNotes());
        appointment.setIdempotencyKey(request.getIdempotencyKey());
        appointment.setExpiresAt(LocalDateTime.now().plusMinutes(EXPIRY_MINUTES));

        appointment = appointmentRepository.save(appointment);
        auditService.log(userId, null, "APPOINTMENT", "CREATE",
                "Appointment", appointment.getId(),
                "Created appointment for slot " + slot.getDate() + " " + slot.getStartTime() + "-" + slot.getEndTime(),
                null);
        return AppointmentResponse.fromEntity(appointment);
    }

    public Page<AppointmentResponse> listAppointments(Long propertyId, String statusFilter,
                                                       String dateFrom, String dateTo,
                                                       String patientName, Long userId,
                                                       String role, Pageable pageable) {
        AppointmentStatus status = null;
        if (statusFilter != null && !statusFilter.isBlank()) {
            try {
                status = AppointmentStatus.valueOf(statusFilter);
            } catch (IllegalArgumentException e) {
                throw new BusinessRuleException("Invalid status: " + statusFilter);
            }
        }

        LocalDateTime from = parseDate(dateFrom, true);
        LocalDateTime to = parseDate(dateTo, false);

        boolean canSeeAll = "ADMIN".equals(role) || "DISPATCHER".equals(role) || "REVIEWER".equals(role);

        Page<Appointment> page;
        if (canSeeAll) {
            page = appointmentRepository.findByFilters(propertyId, status, patientName, from, to, pageable);
        } else if ("SERVICE_STAFF".equals(role)) {
            // Service staff can only see appointments assigned to them
            page = appointmentRepository.findByAssignedStaffAndFilters(userId, propertyId, status, patientName, from, to, pageable);
        } else {
            page = appointmentRepository.findByUserIdAndFilters(userId, propertyId, status, patientName, from, to, pageable);
        }

        return page.map(AppointmentResponse::fromEntity);
    }

    public AppointmentResponse getAppointment(Long id, Long userId, String role) {
        Appointment appointment = findAppointmentOrThrow(id);
        enforceOwnerOrPrivileged(appointment, userId, role);
        return AppointmentResponse.fromEntity(appointment);
    }

    @Transactional
    public AppointmentResponse confirmAppointment(Long id, Long actorId) {
        Appointment appointment = findAppointmentOrThrow(id);

        if (appointment.getStatus() != AppointmentStatus.CREATED) {
            throw new BusinessRuleException("Appointment is not in CREATED status");
        }

        if (appointment.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BusinessRuleException("Appointment has expired and been auto-released");
        }

        appointment.setStatus(AppointmentStatus.CONFIRMED);
        appointment = appointmentRepository.save(appointment);
        auditService.log(actorId, null, "APPOINTMENT", "STATE_CHANGE",
                "Appointment", appointment.getId(), "Confirmed appointment", null);
        return AppointmentResponse.fromEntity(appointment);
    }

    @Transactional
    public AppointmentResponse completeAppointment(Long id, String completionNotes, Long actorId, String role) {
        Appointment appointment = findAppointmentOrThrow(id);

        if (appointment.getStatus() != AppointmentStatus.CONFIRMED) {
            throw new BusinessRuleException("Appointment is not in CONFIRMED status");
        }

        // Service staff can only complete appointments assigned to them
        if ("SERVICE_STAFF".equals(role)) {
            if (appointment.getAssignedServiceStaffId() == null || !appointment.getAssignedServiceStaffId().equals(actorId)) {
                throw new AuthorizationException("Not authorized to complete this appointment — not assigned to you");
            }
        }

        appointment.setStatus(AppointmentStatus.COMPLETED);
        appointment.setCompletionNotes(completionNotes);
        appointment = appointmentRepository.save(appointment);
        auditService.log(actorId, null, "APPOINTMENT", "STATE_CHANGE",
                "Appointment", appointment.getId(), "Completed appointment", null);
        return AppointmentResponse.fromEntity(appointment);
    }

    @Transactional
    public AppointmentResponse assignServiceStaff(Long appointmentId, Long serviceStaffId, Long actorId) {
        Appointment appointment = findAppointmentOrThrow(appointmentId);

        if (appointment.getStatus() != AppointmentStatus.CONFIRMED) {
            throw new BusinessRuleException("Appointment must be in CONFIRMED status to assign staff");
        }

        User staffUser = userRepository.findById(serviceStaffId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + serviceStaffId));
        if (!staffUser.isEnabled()) {
            throw new BusinessRuleException("Target user is disabled");
        }
        if (staffUser.getRole() != Role.SERVICE_STAFF) {
            throw new BusinessRuleException("Target user does not have SERVICE_STAFF role");
        }

        appointment.setAssignedServiceStaffId(serviceStaffId);
        appointment = appointmentRepository.save(appointment);
        auditService.log(actorId, null, "APPOINTMENT", "ASSIGN",
                "Appointment", appointment.getId(),
                "Assigned service staff " + serviceStaffId, null);
        return AppointmentResponse.fromEntity(appointment);
    }

    @Transactional
    public AppointmentResponse cancelAppointment(Long id, String reason, Long userId, String role) {
        Appointment appointment = findAppointmentOrThrow(id);

        if (appointment.getStatus() == AppointmentStatus.COMPLETED ||
            appointment.getStatus() == AppointmentStatus.CANCELED) {
            throw new BusinessRuleException("Appointment is already " + appointment.getStatus().name().toLowerCase());
        }

        if (appointment.getStatus() != AppointmentStatus.CREATED &&
            appointment.getStatus() != AppointmentStatus.CONFIRMED) {
            throw new BusinessRuleException("Appointment cannot be canceled in current status");
        }

        boolean isOwner = appointment.getUserId().equals(userId);
        boolean isPrivileged = "ADMIN".equals(role) || "DISPATCHER".equals(role);
        if (!isOwner && !isPrivileged) {
            throw new AuthorizationException("Not authorized to cancel this appointment");
        }

        appointment.setCancelReason(reason);

        AppointmentSlot slot = slotRepository.findById(appointment.getSlotId())
                .orElseThrow(() -> new ResourceNotFoundException("Slot not found"));
        LocalDateTime appointmentTime = LocalDateTime.of(slot.getDate(), slot.getStartTime());
        long minutesUntilAppointment = ChronoUnit.MINUTES.between(LocalDateTime.now(), appointmentTime);

        if (minutesUntilAppointment < LATE_CANCEL_THRESHOLD_MINUTES) {
            appointment.setStatus(AppointmentStatus.EXCEPTION);
        } else {
            appointment.setStatus(AppointmentStatus.CANCELED);
            slot.setBookedCount(Math.max(0, slot.getBookedCount() - 1));
            slotRepository.save(slot);
        }

        appointment = appointmentRepository.save(appointment);
        auditService.log(userId, null, "APPOINTMENT", "STATE_CHANGE",
                "Appointment", appointment.getId(),
                "Cancel requested → " + appointment.getStatus().name() + ": " + reason, null);
        return AppointmentResponse.fromEntity(appointment);
    }

    @Transactional
    public AppointmentResponse approveCancellation(Long id, String reason, Long actorId) {
        Appointment appointment = findAppointmentOrThrow(id);

        if (appointment.getStatus() != AppointmentStatus.EXCEPTION) {
            throw new BusinessRuleException("Appointment is not in EXCEPTION status");
        }

        appointment.setStatus(AppointmentStatus.CANCELED);
        if (reason != null && !reason.isBlank()) {
            appointment.setCancelReason(reason);
        }

        AppointmentSlot slot = slotRepository.findById(appointment.getSlotId())
                .orElseThrow(() -> new ResourceNotFoundException("Slot not found"));
        slot.setBookedCount(Math.max(0, slot.getBookedCount() - 1));
        slotRepository.save(slot);

        appointment = appointmentRepository.save(appointment);
        auditService.log(actorId, null, "APPOINTMENT", "STATE_CHANGE",
                "Appointment", appointment.getId(), "Cancellation approved", null);
        return AppointmentResponse.fromEntity(appointment);
    }

    @Transactional
    public AppointmentResponse rescheduleAppointment(Long id, Long newSlotId, String reason,
                                                      Long userId, String role) {
        Appointment appointment = findAppointmentOrThrow(id);
        enforceOwnerOrPrivileged(appointment, userId, role);

        if (appointment.getStatus() != AppointmentStatus.CREATED &&
            appointment.getStatus() != AppointmentStatus.CONFIRMED) {
            throw new BusinessRuleException("Appointment cannot be rescheduled in current status");
        }

        if (appointment.getRescheduleCount() >= DEFAULT_MAX_RESCHEDULES) {
            throw new BusinessRuleException("Maximum reschedule limit (" + DEFAULT_MAX_RESCHEDULES + ") reached");
        }

        AppointmentSlot newSlot = slotRepository.findById(newSlotId)
                .orElseThrow(() -> new ResourceNotFoundException("New slot not found with id: " + newSlotId));

        // Use property-configured rules for the new slot's property
        Property newProperty = propertyService.findPropertyOrThrow(newSlot.getPropertyId());
        propertyService.validateBookingEligibility(newProperty);
        int advanceHours = resolveAdvanceBookingHours(newProperty);

        LocalDateTime newAppointmentTime = LocalDateTime.of(newSlot.getDate(), newSlot.getStartTime());
        if (newAppointmentTime.isBefore(LocalDateTime.now().plusHours(advanceHours))) {
            throw new BusinessRuleException("New slot must be at least " + advanceHours + " hours in the future");
        }

        if (newSlot.getBookedCount() >= newSlot.getCapacity()) {
            throw new BusinessRuleException("New slot is at full capacity");
        }

        List<Appointment> overlapping = appointmentRepository.findOverlapping(
                appointment.getUserId(), newSlot.getDate(), newSlot.getStartTime(), newSlot.getEndTime(),
                List.of(AppointmentStatus.CREATED, AppointmentStatus.CONFIRMED), appointment.getId());
        if (!overlapping.isEmpty()) {
            throw new BusinessRuleException("User already has an overlapping appointment during this time");
        }

        AppointmentSlot oldSlot = slotRepository.findById(appointment.getSlotId())
                .orElseThrow(() -> new ResourceNotFoundException("Original slot not found"));
        oldSlot.setBookedCount(Math.max(0, oldSlot.getBookedCount() - 1));
        slotRepository.save(oldSlot);

        try {
            newSlot.setBookedCount(newSlot.getBookedCount() + 1);
            slotRepository.save(newSlot);
        } catch (ObjectOptimisticLockingFailureException e) {
            throw new BusinessRuleException("Slot booking conflict. Please try again");
        }

        appointment.setSlotId(newSlotId);
        appointment.setPropertyId(newSlot.getPropertyId());
        appointment.setRescheduleCount(appointment.getRescheduleCount() + 1);
        appointment.setNotes(reason != null ? reason : appointment.getNotes());

        appointment = appointmentRepository.save(appointment);
        auditService.log(userId, null, "APPOINTMENT", "STATE_CHANGE",
                "Appointment", appointment.getId(),
                "Rescheduled to slot " + newSlotId + " (count: " + appointment.getRescheduleCount() + ")", null);
        return AppointmentResponse.fromEntity(appointment);
    }

    @Scheduled(fixedRate = 60000)
    @Transactional
    public void autoReleaseExpiredAppointments() {
        List<Appointment> expired = appointmentRepository.findByStatusAndExpiresAtBefore(
                AppointmentStatus.CREATED, LocalDateTime.now());

        for (Appointment appointment : expired) {
            appointment.setStatus(AppointmentStatus.CANCELED);
            appointment.setCancelReason("Auto-released: not confirmed within " + EXPIRY_MINUTES + " minutes");
            appointmentRepository.save(appointment);

            slotRepository.findById(appointment.getSlotId()).ifPresent(slot -> {
                slot.setBookedCount(Math.max(0, slot.getBookedCount() - 1));
                slotRepository.save(slot);
            });

            log.info("Auto-released appointment id={}, slotId={}", appointment.getId(), appointment.getSlotId());
            auditService.log(null, "SYSTEM", "APPOINTMENT", "STATE_CHANGE",
                    "Appointment", appointment.getId(), "Auto-released: expired", null);
        }
    }

    public boolean hasActiveAppointments(Long propertyId) {
        return appointmentRepository.existsByPropertyIdAndStatusIn(
                propertyId, List.of(AppointmentStatus.CREATED, AppointmentStatus.CONFIRMED));
    }

    private void enforceOwnerOrPrivileged(Appointment appointment, Long userId, String role) {
        boolean isOwner = appointment.getUserId().equals(userId);
        boolean isAdminOrDispatcherOrReviewer = "ADMIN".equals(role) || "DISPATCHER".equals(role)
                || "REVIEWER".equals(role);
        boolean isAssignedStaff = "SERVICE_STAFF".equals(role)
                && appointment.getAssignedServiceStaffId() != null
                && appointment.getAssignedServiceStaffId().equals(userId);
        if (!isOwner && !isAdminOrDispatcherOrReviewer && !isAssignedStaff) {
            throw new AuthorizationException("Not authorized to access this appointment");
        }
    }

    private Appointment findAppointmentOrThrow(Long id) {
        return appointmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Appointment not found with id: " + id));
    }

    private int resolveAdvanceBookingHours(Property property) {
        return property.getMinBookingLeadHours() != null
                ? property.getMinBookingLeadHours() : DEFAULT_ADVANCE_BOOKING_HOURS;
    }

    private int resolveMaxBookingDaysAhead(Property property) {
        return property.getMaxBookingLeadDays() != null
                ? property.getMaxBookingLeadDays() : DEFAULT_MAX_BOOKING_DAYS_AHEAD;
    }

    private LocalDateTime parseDate(String dateStr, boolean startOfDay) {
        if (dateStr == null) {
            return null;
        }
        try {
            LocalDate date = LocalDate.parse(dateStr);
            return startOfDay ? date.atStartOfDay() : date.atTime(23, 59, 59);
        } catch (DateTimeParseException e) {
            throw new BusinessRuleException("Invalid date format: " + dateStr + ". Expected yyyy-MM-dd");
        }
    }
}
