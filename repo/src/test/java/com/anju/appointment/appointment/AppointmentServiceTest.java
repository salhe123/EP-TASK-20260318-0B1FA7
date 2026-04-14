package com.anju.appointment.appointment;

import com.anju.appointment.appointment.dto.AppointmentResponse;
import com.anju.appointment.appointment.dto.BookAppointmentRequest;
import com.anju.appointment.appointment.dto.SlotGenerateRequest;
import com.anju.appointment.appointment.dto.SlotGenerateResponse;
import com.anju.appointment.appointment.entity.Appointment;
import com.anju.appointment.appointment.entity.AppointmentSlot;
import com.anju.appointment.appointment.entity.AppointmentStatus;
import com.anju.appointment.appointment.repository.AppointmentRepository;
import com.anju.appointment.appointment.repository.AppointmentSlotRepository;
import com.anju.appointment.appointment.service.AppointmentService;
import com.anju.appointment.audit.service.AuditService;
import com.anju.appointment.common.BusinessRuleException;
import com.anju.appointment.common.ResourceNotFoundException;
import com.anju.appointment.property.entity.Property;
import com.anju.appointment.property.service.PropertyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AppointmentServiceTest {

    @Mock
    private AppointmentSlotRepository slotRepository;

    @Mock
    private AppointmentRepository appointmentRepository;

    @Mock
    private PropertyService propertyService;

    @Mock
    private AuditService auditService;

    @InjectMocks
    private AppointmentService appointmentService;

    @Captor
    private ArgumentCaptor<List<AppointmentSlot>> slotListCaptor;

    @Captor
    private ArgumentCaptor<Appointment> appointmentCaptor;

    private Property defaultProperty;

    @BeforeEach
    void setUp() {
        defaultProperty = new Property();
        defaultProperty.setId(1L);
        defaultProperty.setName("Test Clinic");
        defaultProperty.setType("CLINIC");
        defaultProperty.setCapacity(3);
    }

    // -------------------------------------------------------------------------
    // Helper builders
    // -------------------------------------------------------------------------

    private SlotGenerateRequest buildSlotRequest(Long propertyId, LocalDate date,
                                                  int duration, LocalTime start,
                                                  LocalTime end, Integer capacity) {
        SlotGenerateRequest req = new SlotGenerateRequest();
        req.setPropertyId(propertyId);
        req.setDate(date);
        req.setSlotDuration(duration);
        req.setStartTime(start);
        req.setEndTime(end);
        req.setCapacity(capacity);
        return req;
    }

    private AppointmentSlot buildSlot(Long id, Long propertyId, LocalDate date,
                                       LocalTime start, LocalTime end,
                                       int duration, int capacity, int bookedCount) {
        AppointmentSlot slot = new AppointmentSlot();
        slot.setId(id);
        slot.setPropertyId(propertyId);
        slot.setDate(date);
        slot.setStartTime(start);
        slot.setEndTime(end);
        slot.setDuration(duration);
        slot.setCapacity(capacity);
        slot.setBookedCount(bookedCount);
        return slot;
    }

    private Appointment buildAppointment(Long id, Long slotId, Long propertyId,
                                          Long userId, AppointmentStatus status) {
        Appointment appt = new Appointment();
        appt.setId(id);
        appt.setSlotId(slotId);
        appt.setPropertyId(propertyId);
        appt.setUserId(userId);
        appt.setPatientName("John Doe");
        appt.setPatientPhone("13812345678");
        appt.setServiceType("GENERAL");
        appt.setStatus(status);
        appt.setIdempotencyKey("idem-" + id);
        appt.setExpiresAt(LocalDateTime.now().plusMinutes(15));
        appt.setRescheduleCount(0);
        return appt;
    }

    private BookAppointmentRequest buildBookRequest(Long slotId, Long propertyId,
                                                     String idempotencyKey) {
        BookAppointmentRequest req = new BookAppointmentRequest();
        req.setSlotId(slotId);
        req.setPropertyId(propertyId);
        req.setPatientName("John Doe");
        req.setPatientPhone("13812345678");
        req.setServiceType("GENERAL");
        req.setNotes("Test notes");
        req.setIdempotencyKey(idempotencyKey);
        return req;
    }

    /**
     * Returns a slot whose date/startTime is {@code hoursFromNow} hours in the future.
     */
    private AppointmentSlot buildFutureSlot(Long id, Long propertyId, int hoursFromNow,
                                             int capacity, int bookedCount) {
        LocalDateTime future = LocalDateTime.now().plusHours(hoursFromNow);
        return buildSlot(id, propertyId, future.toLocalDate(), future.toLocalTime(),
                future.toLocalTime().plusMinutes(30), 30, capacity, bookedCount);
    }

    // =========================================================================
    // 1. generateSlots
    // =========================================================================

    @Nested
    @DisplayName("generateSlots")
    class GenerateSlots {

        @Test
        @DisplayName("valid request creates correct number of slots")
        void validRequest_createsCorrectSlots() {
            SlotGenerateRequest req = buildSlotRequest(1L, LocalDate.now().plusDays(1),
                    30, LocalTime.of(9, 0), LocalTime.of(11, 0), null);

            when(propertyService.findPropertyOrThrow(1L)).thenReturn(defaultProperty);
            when(slotRepository.existsByPropertyIdAndDate(1L, req.getDate())).thenReturn(false);

            SlotGenerateResponse resp = appointmentService.generateSlots(req);

            assertEquals(4, resp.getSlotsGenerated());  // 09:00-09:30, 09:30-10:00, 10:00-10:30, 10:30-11:00
            assertEquals(1L, resp.getPropertyId());
            assertEquals(30, resp.getSlotDuration());

            verify(slotRepository).saveAll(slotListCaptor.capture());
            List<AppointmentSlot> saved = slotListCaptor.getValue();
            assertEquals(4, saved.size());
            assertEquals(LocalTime.of(9, 0), saved.get(0).getStartTime());
            assertEquals(LocalTime.of(9, 30), saved.get(0).getEndTime());
            assertEquals(defaultProperty.getCapacity(), saved.get(0).getCapacity());
        }

        @Test
        @DisplayName("custom capacity overrides property default")
        void customCapacity_overridesPropertyDefault() {
            SlotGenerateRequest req = buildSlotRequest(1L, LocalDate.now().plusDays(1),
                    60, LocalTime.of(9, 0), LocalTime.of(11, 0), 5);

            when(propertyService.findPropertyOrThrow(1L)).thenReturn(defaultProperty);
            when(slotRepository.existsByPropertyIdAndDate(1L, req.getDate())).thenReturn(false);

            SlotGenerateResponse resp = appointmentService.generateSlots(req);

            assertEquals(2, resp.getSlotsGenerated());
            verify(slotRepository).saveAll(slotListCaptor.capture());
            assertEquals(5, slotListCaptor.getValue().get(0).getCapacity());
        }

        @ParameterizedTest
        @ValueSource(ints = {10, 20, 45, 120})
        @DisplayName("invalid duration throws BusinessRuleException")
        void invalidDuration_throws(int badDuration) {
            SlotGenerateRequest req = buildSlotRequest(1L, LocalDate.now().plusDays(1),
                    badDuration, LocalTime.of(9, 0), LocalTime.of(10, 0), null);

            BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                    () -> appointmentService.generateSlots(req));
            assertTrue(ex.getMessage().contains("15, 30, 60, 90"));
        }

        @Test
        @DisplayName("duplicate date throws BusinessRuleException")
        void duplicateDate_throws() {
            SlotGenerateRequest req = buildSlotRequest(1L, LocalDate.now().plusDays(1),
                    30, LocalTime.of(9, 0), LocalTime.of(10, 0), null);

            when(propertyService.findPropertyOrThrow(1L)).thenReturn(defaultProperty);
            when(slotRepository.existsByPropertyIdAndDate(1L, req.getDate())).thenReturn(true);

            BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                    () -> appointmentService.generateSlots(req));
            assertTrue(ex.getMessage().contains("already exist"));
        }
    }

    // =========================================================================
    // 2. bookAppointment
    // =========================================================================

    @Nested
    @DisplayName("bookAppointment")
    class BookAppointment {

        @Test
        @DisplayName("success - creates appointment and increments booked count")
        void success() {
            Long userId = 100L;
            AppointmentSlot slot = buildFutureSlot(10L, 1L, 5, 3, 0);
            BookAppointmentRequest req = buildBookRequest(10L, 1L, "key-1");

            when(appointmentRepository.findByIdempotencyKey("key-1")).thenReturn(Optional.empty());
            when(slotRepository.findById(10L)).thenReturn(Optional.of(slot));
            when(propertyService.findPropertyOrThrow(1L)).thenReturn(defaultProperty);
            when(appointmentRepository.findOverlapping(eq(userId), any(), any(), any(), any(), isNull()))
                    .thenReturn(Collections.emptyList());
            when(appointmentRepository.save(any(Appointment.class))).thenAnswer(inv -> {
                Appointment a = inv.getArgument(0);
                a.setId(1L);
                return a;
            });

            AppointmentResponse resp = appointmentService.bookAppointment(req, userId);

            assertNotNull(resp);
            assertEquals(1, slot.getBookedCount());
            verify(slotRepository).save(slot);
            verify(appointmentRepository).save(any(Appointment.class));
            verify(auditService).log(eq(userId), isNull(), eq("APPOINTMENT"), eq("CREATE"),
                    eq("Appointment"), eq(1L), contains("Created appointment"), isNull());
        }

        @Test
        @DisplayName("idempotency - returns existing appointment without creating new one")
        void idempotency_returnsExisting() {
            Appointment existing = buildAppointment(1L, 10L, 1L, 100L, AppointmentStatus.CREATED);
            BookAppointmentRequest req = buildBookRequest(10L, 1L, "idem-1");

            when(appointmentRepository.findByIdempotencyKey("idem-1")).thenReturn(Optional.of(existing));

            AppointmentResponse resp = appointmentService.bookAppointment(req, 100L);

            assertNotNull(resp);
            assertEquals(1L, resp.getId());
            verify(slotRepository, never()).findById(anyLong());
            verify(appointmentRepository, never()).save(any(Appointment.class));
        }

        @Test
        @DisplayName("slot not found throws ResourceNotFoundException")
        void slotNotFound_throws() {
            BookAppointmentRequest req = buildBookRequest(999L, 1L, "key-2");
            when(appointmentRepository.findByIdempotencyKey("key-2")).thenReturn(Optional.empty());
            when(slotRepository.findById(999L)).thenReturn(Optional.empty());

            assertThrows(ResourceNotFoundException.class,
                    () -> appointmentService.bookAppointment(req, 100L));
        }

        @Test
        @DisplayName("compliance failure throws BusinessRuleException")
        void complianceFailure_throws() {
            AppointmentSlot slot = buildFutureSlot(10L, 1L, 5, 3, 0);
            BookAppointmentRequest req = buildBookRequest(10L, 1L, "key-3");

            when(appointmentRepository.findByIdempotencyKey("key-3")).thenReturn(Optional.empty());
            when(slotRepository.findById(10L)).thenReturn(Optional.of(slot));
            when(propertyService.findPropertyOrThrow(1L)).thenReturn(defaultProperty);
            doThrow(new BusinessRuleException("Property is non-compliant and cannot be used for bookings"))
                    .when(propertyService).validateCompliance(defaultProperty);

            BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                    () -> appointmentService.bookAppointment(req, 100L));
            assertTrue(ex.getMessage().contains("non-compliant"));
        }

        @Test
        @DisplayName("too early (<2 hours) throws BusinessRuleException")
        void tooEarly_throws() {
            // Slot starting in 1 hour
            AppointmentSlot slot = buildFutureSlot(10L, 1L, 1, 3, 0);
            BookAppointmentRequest req = buildBookRequest(10L, 1L, "key-4");

            when(appointmentRepository.findByIdempotencyKey("key-4")).thenReturn(Optional.empty());
            when(slotRepository.findById(10L)).thenReturn(Optional.of(slot));
            when(propertyService.findPropertyOrThrow(1L)).thenReturn(defaultProperty);

            BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                    () -> appointmentService.bookAppointment(req, 100L));
            assertTrue(ex.getMessage().contains("at least 2 hours"));
        }

        @Test
        @DisplayName("too far ahead (>14 days) throws BusinessRuleException")
        void tooFarAhead_throws() {
            LocalDate farDate = LocalDate.now().plusDays(15);
            AppointmentSlot slot = buildSlot(10L, 1L, farDate,
                    LocalTime.of(10, 0), LocalTime.of(10, 30), 30, 3, 0);
            BookAppointmentRequest req = buildBookRequest(10L, 1L, "key-5");

            when(appointmentRepository.findByIdempotencyKey("key-5")).thenReturn(Optional.empty());
            when(slotRepository.findById(10L)).thenReturn(Optional.of(slot));
            when(propertyService.findPropertyOrThrow(1L)).thenReturn(defaultProperty);

            BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                    () -> appointmentService.bookAppointment(req, 100L));
            assertTrue(ex.getMessage().contains("14 days"));
        }

        @Test
        @DisplayName("slot full throws BusinessRuleException")
        void slotFull_throws() {
            AppointmentSlot slot = buildFutureSlot(10L, 1L, 5, 3, 3);
            BookAppointmentRequest req = buildBookRequest(10L, 1L, "key-6");

            when(appointmentRepository.findByIdempotencyKey("key-6")).thenReturn(Optional.empty());
            when(slotRepository.findById(10L)).thenReturn(Optional.of(slot));
            when(propertyService.findPropertyOrThrow(1L)).thenReturn(defaultProperty);

            BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                    () -> appointmentService.bookAppointment(req, 100L));
            assertTrue(ex.getMessage().contains("full capacity"));
        }

        @Test
        @DisplayName("overlapping appointment throws BusinessRuleException")
        void overlapping_throws() {
            Long userId = 100L;
            AppointmentSlot slot = buildFutureSlot(10L, 1L, 5, 3, 0);
            BookAppointmentRequest req = buildBookRequest(10L, 1L, "key-7");

            Appointment overlap = buildAppointment(99L, 10L, 1L, userId, AppointmentStatus.CONFIRMED);

            when(appointmentRepository.findByIdempotencyKey("key-7")).thenReturn(Optional.empty());
            when(slotRepository.findById(10L)).thenReturn(Optional.of(slot));
            when(propertyService.findPropertyOrThrow(1L)).thenReturn(defaultProperty);
            when(appointmentRepository.findOverlapping(eq(userId), any(), any(), any(), any(), isNull()))
                    .thenReturn(List.of(overlap));

            BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                    () -> appointmentService.bookAppointment(req, userId));
            assertTrue(ex.getMessage().contains("overlapping"));
        }
    }

    // =========================================================================
    // 3. getAppointment
    // =========================================================================

    @Nested
    @DisplayName("getAppointment")
    class GetAppointment {

        @Test
        @DisplayName("owner access succeeds")
        void ownerAccess_ok() {
            Appointment appt = buildAppointment(1L, 10L, 1L, 100L, AppointmentStatus.CREATED);
            when(appointmentRepository.findById(1L)).thenReturn(Optional.of(appt));

            AppointmentResponse resp = appointmentService.getAppointment(1L, 100L, "PATIENT");

            assertNotNull(resp);
            assertEquals(1L, resp.getId());
        }

        @Test
        @DisplayName("admin access succeeds even when not owner")
        void adminAccess_ok() {
            Appointment appt = buildAppointment(1L, 10L, 1L, 100L, AppointmentStatus.CREATED);
            when(appointmentRepository.findById(1L)).thenReturn(Optional.of(appt));

            AppointmentResponse resp = appointmentService.getAppointment(1L, 999L, "ADMIN");

            assertNotNull(resp);
            assertEquals(1L, resp.getId());
        }

        @Test
        @DisplayName("non-owner non-privileged throws BusinessRuleException")
        void nonOwnerNonPrivileged_throws() {
            Appointment appt = buildAppointment(1L, 10L, 1L, 100L, AppointmentStatus.CREATED);
            when(appointmentRepository.findById(1L)).thenReturn(Optional.of(appt));

            BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                    () -> appointmentService.getAppointment(1L, 999L, "PATIENT"));
            assertTrue(ex.getMessage().contains("Not authorized"));
        }

        @Test
        @DisplayName("appointment not found throws ResourceNotFoundException")
        void notFound_throws() {
            when(appointmentRepository.findById(999L)).thenReturn(Optional.empty());

            assertThrows(ResourceNotFoundException.class,
                    () -> appointmentService.getAppointment(999L, 100L, "ADMIN"));
        }
    }

    // =========================================================================
    // 4. confirmAppointment
    // =========================================================================

    @Nested
    @DisplayName("confirmAppointment")
    class ConfirmAppointment {

        @Test
        @DisplayName("success from CREATED status")
        void success() {
            Appointment appt = buildAppointment(1L, 10L, 1L, 100L, AppointmentStatus.CREATED);
            appt.setExpiresAt(LocalDateTime.now().plusMinutes(10));
            when(appointmentRepository.findById(1L)).thenReturn(Optional.of(appt));
            when(appointmentRepository.save(any(Appointment.class))).thenAnswer(inv -> inv.getArgument(0));

            AppointmentResponse resp = appointmentService.confirmAppointment(1L);

            assertEquals("CONFIRMED", resp.getStatus());
            verify(auditService).log(isNull(), isNull(), eq("APPOINTMENT"), eq("STATE_CHANGE"),
                    eq("Appointment"), eq(1L), contains("Confirmed"), isNull());
        }

        @Test
        @DisplayName("wrong status (CONFIRMED) throws BusinessRuleException")
        void wrongStatus_throws() {
            Appointment appt = buildAppointment(1L, 10L, 1L, 100L, AppointmentStatus.CONFIRMED);
            when(appointmentRepository.findById(1L)).thenReturn(Optional.of(appt));

            BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                    () -> appointmentService.confirmAppointment(1L));
            assertTrue(ex.getMessage().contains("not in CREATED status"));
        }

        @Test
        @DisplayName("expired appointment throws BusinessRuleException")
        void expired_throws() {
            Appointment appt = buildAppointment(1L, 10L, 1L, 100L, AppointmentStatus.CREATED);
            appt.setExpiresAt(LocalDateTime.now().minusMinutes(5));
            when(appointmentRepository.findById(1L)).thenReturn(Optional.of(appt));

            BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                    () -> appointmentService.confirmAppointment(1L));
            assertTrue(ex.getMessage().contains("expired"));
        }
    }

    // =========================================================================
    // 5. completeAppointment
    // =========================================================================

    @Nested
    @DisplayName("completeAppointment")
    class CompleteAppointment {

        @Test
        @DisplayName("success from CONFIRMED status")
        void success() {
            Appointment appt = buildAppointment(1L, 10L, 1L, 100L, AppointmentStatus.CONFIRMED);
            when(appointmentRepository.findById(1L)).thenReturn(Optional.of(appt));
            when(appointmentRepository.save(any(Appointment.class))).thenAnswer(inv -> inv.getArgument(0));

            AppointmentResponse resp = appointmentService.completeAppointment(1L, "Treatment done");

            assertEquals("COMPLETED", resp.getStatus());
            assertEquals("Treatment done", resp.getCompletionNotes());
            verify(auditService).log(isNull(), isNull(), eq("APPOINTMENT"), eq("STATE_CHANGE"),
                    eq("Appointment"), eq(1L), contains("Completed"), isNull());
        }

        @Test
        @DisplayName("wrong status (CREATED) throws BusinessRuleException")
        void wrongStatus_throws() {
            Appointment appt = buildAppointment(1L, 10L, 1L, 100L, AppointmentStatus.CREATED);
            when(appointmentRepository.findById(1L)).thenReturn(Optional.of(appt));

            BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                    () -> appointmentService.completeAppointment(1L, "notes"));
            assertTrue(ex.getMessage().contains("not in CONFIRMED status"));
        }

        @Test
        @DisplayName("wrong status (CANCELED) throws BusinessRuleException")
        void canceledStatus_throws() {
            Appointment appt = buildAppointment(1L, 10L, 1L, 100L, AppointmentStatus.CANCELED);
            when(appointmentRepository.findById(1L)).thenReturn(Optional.of(appt));

            BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                    () -> appointmentService.completeAppointment(1L, "notes"));
            assertTrue(ex.getMessage().contains("not in CONFIRMED status"));
        }
    }

    // =========================================================================
    // 6. cancelAppointment
    // =========================================================================

    @Nested
    @DisplayName("cancelAppointment")
    class CancelAppointment {

        @Test
        @DisplayName("direct cancel (>=1 hour) sets CANCELED and frees slot")
        void directCancel_success() {
            Long userId = 100L;
            Appointment appt = buildAppointment(1L, 10L, 1L, userId, AppointmentStatus.CONFIRMED);
            // Slot far in the future (>1 hour ahead)
            AppointmentSlot slot = buildFutureSlot(10L, 1L, 3, 3, 2);

            when(appointmentRepository.findById(1L)).thenReturn(Optional.of(appt));
            when(slotRepository.findById(10L)).thenReturn(Optional.of(slot));
            when(appointmentRepository.save(any(Appointment.class))).thenAnswer(inv -> inv.getArgument(0));

            AppointmentResponse resp = appointmentService.cancelAppointment(1L, "No longer needed", userId, "PATIENT");

            assertEquals("CANCELED", resp.getStatus());
            assertEquals(1, slot.getBookedCount()); // decremented from 2
            verify(slotRepository).save(slot);
            verify(auditService).log(eq(userId), isNull(), eq("APPOINTMENT"), eq("STATE_CHANGE"),
                    eq("Appointment"), eq(1L), contains("CANCELED"), isNull());
        }

        @Test
        @DisplayName("late cancel (<1 hour) sets EXCEPTION status")
        void lateCancel_setsException() {
            Long userId = 100L;
            Appointment appt = buildAppointment(1L, 10L, 1L, userId, AppointmentStatus.CONFIRMED);
            // Slot starting in ~30 minutes (< 60 min threshold)
            LocalDateTime soon = LocalDateTime.now().plusMinutes(30);
            AppointmentSlot slot = buildSlot(10L, 1L, soon.toLocalDate(),
                    soon.toLocalTime(), soon.toLocalTime().plusMinutes(30), 30, 3, 2);

            when(appointmentRepository.findById(1L)).thenReturn(Optional.of(appt));
            when(slotRepository.findById(10L)).thenReturn(Optional.of(slot));
            when(appointmentRepository.save(any(Appointment.class))).thenAnswer(inv -> inv.getArgument(0));

            AppointmentResponse resp = appointmentService.cancelAppointment(1L, "Urgent change", userId, "PATIENT");

            assertEquals("EXCEPTION", resp.getStatus());
            // Slot booked count should NOT be decremented for EXCEPTION
            assertEquals(2, slot.getBookedCount());
            verify(slotRepository, never()).save(slot);
            verify(auditService).log(eq(userId), isNull(), eq("APPOINTMENT"), eq("STATE_CHANGE"),
                    eq("Appointment"), eq(1L), contains("EXCEPTION"), isNull());
        }

        @Test
        @DisplayName("non-owner non-privileged throws BusinessRuleException")
        void nonOwnerNonPrivileged_throws() {
            Appointment appt = buildAppointment(1L, 10L, 1L, 100L, AppointmentStatus.CONFIRMED);
            when(appointmentRepository.findById(1L)).thenReturn(Optional.of(appt));

            BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                    () -> appointmentService.cancelAppointment(1L, "reason", 999L, "PATIENT"));
            assertTrue(ex.getMessage().contains("Not authorized"));
        }

        @Test
        @DisplayName("admin can cancel another user's appointment")
        void adminCanCancel() {
            Appointment appt = buildAppointment(1L, 10L, 1L, 100L, AppointmentStatus.CONFIRMED);
            AppointmentSlot slot = buildFutureSlot(10L, 1L, 3, 3, 1);

            when(appointmentRepository.findById(1L)).thenReturn(Optional.of(appt));
            when(slotRepository.findById(10L)).thenReturn(Optional.of(slot));
            when(appointmentRepository.save(any(Appointment.class))).thenAnswer(inv -> inv.getArgument(0));

            AppointmentResponse resp = appointmentService.cancelAppointment(1L, "Admin cancel", 999L, "ADMIN");

            assertEquals("CANCELED", resp.getStatus());
        }

        @Test
        @DisplayName("dispatcher can cancel another user's appointment")
        void dispatcherCanCancel() {
            Appointment appt = buildAppointment(1L, 10L, 1L, 100L, AppointmentStatus.CREATED);
            AppointmentSlot slot = buildFutureSlot(10L, 1L, 3, 3, 1);

            when(appointmentRepository.findById(1L)).thenReturn(Optional.of(appt));
            when(slotRepository.findById(10L)).thenReturn(Optional.of(slot));
            when(appointmentRepository.save(any(Appointment.class))).thenAnswer(inv -> inv.getArgument(0));

            AppointmentResponse resp = appointmentService.cancelAppointment(1L, "Dispatch cancel", 999L, "DISPATCHER");

            assertEquals("CANCELED", resp.getStatus());
        }

        @Test
        @DisplayName("already completed throws BusinessRuleException")
        void alreadyCompleted_throws() {
            Appointment appt = buildAppointment(1L, 10L, 1L, 100L, AppointmentStatus.COMPLETED);
            when(appointmentRepository.findById(1L)).thenReturn(Optional.of(appt));

            BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                    () -> appointmentService.cancelAppointment(1L, "reason", 100L, "PATIENT"));
            assertTrue(ex.getMessage().contains("already completed"));
        }

        @Test
        @DisplayName("already canceled throws BusinessRuleException")
        void alreadyCanceled_throws() {
            Appointment appt = buildAppointment(1L, 10L, 1L, 100L, AppointmentStatus.CANCELED);
            when(appointmentRepository.findById(1L)).thenReturn(Optional.of(appt));

            BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                    () -> appointmentService.cancelAppointment(1L, "reason", 100L, "PATIENT"));
            assertTrue(ex.getMessage().contains("already canceled"));
        }
    }

    // =========================================================================
    // 7. approveCancellation
    // =========================================================================

    @Nested
    @DisplayName("approveCancellation")
    class ApproveCancellation {

        @Test
        @DisplayName("success from EXCEPTION status")
        void success() {
            Appointment appt = buildAppointment(1L, 10L, 1L, 100L, AppointmentStatus.EXCEPTION);
            AppointmentSlot slot = buildFutureSlot(10L, 1L, 3, 3, 2);

            when(appointmentRepository.findById(1L)).thenReturn(Optional.of(appt));
            when(slotRepository.findById(10L)).thenReturn(Optional.of(slot));
            when(appointmentRepository.save(any(Appointment.class))).thenAnswer(inv -> inv.getArgument(0));

            AppointmentResponse resp = appointmentService.approveCancellation(1L, "Approved by admin");

            assertEquals("CANCELED", resp.getStatus());
            assertEquals(1, slot.getBookedCount()); // decremented from 2
            verify(slotRepository).save(slot);
            verify(auditService).log(isNull(), isNull(), eq("APPOINTMENT"), eq("STATE_CHANGE"),
                    eq("Appointment"), eq(1L), contains("Cancellation approved"), isNull());
        }

        @Test
        @DisplayName("null reason keeps existing cancel reason")
        void nullReason_keepsExisting() {
            Appointment appt = buildAppointment(1L, 10L, 1L, 100L, AppointmentStatus.EXCEPTION);
            appt.setCancelReason("Original reason");
            AppointmentSlot slot = buildFutureSlot(10L, 1L, 3, 3, 1);

            when(appointmentRepository.findById(1L)).thenReturn(Optional.of(appt));
            when(slotRepository.findById(10L)).thenReturn(Optional.of(slot));
            when(appointmentRepository.save(any(Appointment.class))).thenAnswer(inv -> inv.getArgument(0));

            appointmentService.approveCancellation(1L, null);

            assertEquals("Original reason", appt.getCancelReason());
        }

        @Test
        @DisplayName("wrong status (CONFIRMED) throws BusinessRuleException")
        void wrongStatus_throws() {
            Appointment appt = buildAppointment(1L, 10L, 1L, 100L, AppointmentStatus.CONFIRMED);
            when(appointmentRepository.findById(1L)).thenReturn(Optional.of(appt));

            BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                    () -> appointmentService.approveCancellation(1L, "reason"));
            assertTrue(ex.getMessage().contains("not in EXCEPTION status"));
        }

        @Test
        @DisplayName("wrong status (CREATED) throws BusinessRuleException")
        void createdStatus_throws() {
            Appointment appt = buildAppointment(1L, 10L, 1L, 100L, AppointmentStatus.CREATED);
            when(appointmentRepository.findById(1L)).thenReturn(Optional.of(appt));

            BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                    () -> appointmentService.approveCancellation(1L, "reason"));
            assertTrue(ex.getMessage().contains("not in EXCEPTION status"));
        }
    }

    // =========================================================================
    // 8. rescheduleAppointment
    // =========================================================================

    @Nested
    @DisplayName("rescheduleAppointment")
    class RescheduleAppointment {

        @Test
        @DisplayName("success - reschedules, updates counts, logs audit")
        void success() {
            Long userId = 100L;
            Appointment appt = buildAppointment(1L, 10L, 1L, userId, AppointmentStatus.CONFIRMED);
            appt.setRescheduleCount(0);

            AppointmentSlot oldSlot = buildFutureSlot(10L, 1L, 5, 3, 2);
            AppointmentSlot newSlot = buildFutureSlot(20L, 1L, 6, 3, 0);

            when(appointmentRepository.findById(1L)).thenReturn(Optional.of(appt));
            when(slotRepository.findById(10L)).thenReturn(Optional.of(oldSlot));
            when(slotRepository.findById(20L)).thenReturn(Optional.of(newSlot));
            when(appointmentRepository.findOverlapping(eq(userId), any(), any(), any(), any(), eq(1L)))
                    .thenReturn(Collections.emptyList());
            when(appointmentRepository.save(any(Appointment.class))).thenAnswer(inv -> inv.getArgument(0));

            AppointmentResponse resp = appointmentService.rescheduleAppointment(1L, 20L, "New time", userId, "PATIENT");

            assertNotNull(resp);
            assertEquals(20L, appt.getSlotId());
            assertEquals(1, appt.getRescheduleCount());
            assertEquals(1, oldSlot.getBookedCount()); // decremented from 2
            assertEquals(1, newSlot.getBookedCount());  // incremented from 0
            verify(slotRepository).save(oldSlot);
            verify(slotRepository).save(newSlot);
            verify(auditService).log(isNull(), isNull(), eq("APPOINTMENT"), eq("STATE_CHANGE"),
                    eq("Appointment"), eq(1L), contains("Rescheduled"), isNull());
        }

        @Test
        @DisplayName("max reschedules reached throws BusinessRuleException")
        void maxReschedules_throws() {
            Long userId = 100L;
            Appointment appt = buildAppointment(1L, 10L, 1L, userId, AppointmentStatus.CONFIRMED);
            appt.setRescheduleCount(2); // already at max (MAX_RESCHEDULES = 2)

            when(appointmentRepository.findById(1L)).thenReturn(Optional.of(appt));

            BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                    () -> appointmentService.rescheduleAppointment(1L, 20L, "reason", userId, "PATIENT"));
            assertTrue(ex.getMessage().contains("Maximum reschedule limit"));
        }

        @Test
        @DisplayName("new slot full throws BusinessRuleException")
        void newSlotFull_throws() {
            Long userId = 100L;
            Appointment appt = buildAppointment(1L, 10L, 1L, userId, AppointmentStatus.CONFIRMED);
            AppointmentSlot newSlot = buildFutureSlot(20L, 1L, 6, 3, 3); // full

            when(appointmentRepository.findById(1L)).thenReturn(Optional.of(appt));
            when(slotRepository.findById(20L)).thenReturn(Optional.of(newSlot));

            BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                    () -> appointmentService.rescheduleAppointment(1L, 20L, "reason", userId, "PATIENT"));
            assertTrue(ex.getMessage().contains("full capacity"));
        }

        @Test
        @DisplayName("non-owner non-privileged throws BusinessRuleException")
        void nonOwnerNonPrivileged_throws() {
            Appointment appt = buildAppointment(1L, 10L, 1L, 100L, AppointmentStatus.CONFIRMED);
            when(appointmentRepository.findById(1L)).thenReturn(Optional.of(appt));

            BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                    () -> appointmentService.rescheduleAppointment(1L, 20L, "reason", 999L, "PATIENT"));
            assertTrue(ex.getMessage().contains("Not authorized"));
        }

        @Test
        @DisplayName("wrong status (COMPLETED) throws BusinessRuleException")
        void wrongStatus_throws() {
            Long userId = 100L;
            Appointment appt = buildAppointment(1L, 10L, 1L, userId, AppointmentStatus.COMPLETED);

            when(appointmentRepository.findById(1L)).thenReturn(Optional.of(appt));

            BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                    () -> appointmentService.rescheduleAppointment(1L, 20L, "reason", userId, "PATIENT"));
            assertTrue(ex.getMessage().contains("cannot be rescheduled"));
        }

        @Test
        @DisplayName("new slot not found throws ResourceNotFoundException")
        void newSlotNotFound_throws() {
            Long userId = 100L;
            Appointment appt = buildAppointment(1L, 10L, 1L, userId, AppointmentStatus.CONFIRMED);

            when(appointmentRepository.findById(1L)).thenReturn(Optional.of(appt));
            when(slotRepository.findById(20L)).thenReturn(Optional.empty());

            assertThrows(ResourceNotFoundException.class,
                    () -> appointmentService.rescheduleAppointment(1L, 20L, "reason", userId, "PATIENT"));
        }

        @Test
        @DisplayName("overlapping appointment at new slot throws BusinessRuleException")
        void overlappingAtNewSlot_throws() {
            Long userId = 100L;
            Appointment appt = buildAppointment(1L, 10L, 1L, userId, AppointmentStatus.CONFIRMED);
            AppointmentSlot newSlot = buildFutureSlot(20L, 1L, 6, 3, 0);

            Appointment overlap = buildAppointment(99L, 20L, 1L, userId, AppointmentStatus.CONFIRMED);

            when(appointmentRepository.findById(1L)).thenReturn(Optional.of(appt));
            when(slotRepository.findById(20L)).thenReturn(Optional.of(newSlot));
            when(appointmentRepository.findOverlapping(eq(userId), any(), any(), any(), any(), eq(1L)))
                    .thenReturn(List.of(overlap));

            BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                    () -> appointmentService.rescheduleAppointment(1L, 20L, "reason", userId, "PATIENT"));
            assertTrue(ex.getMessage().contains("overlapping"));
        }
    }

    // =========================================================================
    // 9. autoReleaseExpiredAppointments
    // =========================================================================

    @Nested
    @DisplayName("autoReleaseExpiredAppointments")
    class AutoReleaseExpiredAppointments {

        @Test
        @DisplayName("cancels expired appointments and frees slots")
        void cancelsExpiredAndFreesSlots() {
            Appointment expired1 = buildAppointment(1L, 10L, 1L, 100L, AppointmentStatus.CREATED);
            expired1.setExpiresAt(LocalDateTime.now().minusMinutes(5));

            Appointment expired2 = buildAppointment(2L, 20L, 1L, 200L, AppointmentStatus.CREATED);
            expired2.setExpiresAt(LocalDateTime.now().minusMinutes(1));

            AppointmentSlot slot1 = buildFutureSlot(10L, 1L, 5, 3, 2);
            AppointmentSlot slot2 = buildFutureSlot(20L, 1L, 5, 3, 1);

            when(appointmentRepository.findByStatusAndExpiresAtBefore(eq(AppointmentStatus.CREATED), any(LocalDateTime.class)))
                    .thenReturn(List.of(expired1, expired2));
            when(slotRepository.findById(10L)).thenReturn(Optional.of(slot1));
            when(slotRepository.findById(20L)).thenReturn(Optional.of(slot2));

            appointmentService.autoReleaseExpiredAppointments();

            // Both appointments should be saved as CANCELED
            verify(appointmentRepository, times(2)).save(appointmentCaptor.capture());
            List<Appointment> savedAppointments = appointmentCaptor.getAllValues();
            assertTrue(savedAppointments.stream().allMatch(a -> a.getStatus() == AppointmentStatus.CANCELED));
            assertTrue(savedAppointments.stream().allMatch(a -> a.getCancelReason().contains("Auto-released")));

            // Slots should be freed
            assertEquals(1, slot1.getBookedCount());
            assertEquals(0, slot2.getBookedCount());
            verify(slotRepository).save(slot1);
            verify(slotRepository).save(slot2);

            // Audit logged for each
            verify(auditService, times(2)).log(isNull(), eq("SYSTEM"), eq("APPOINTMENT"),
                    eq("STATE_CHANGE"), eq("Appointment"), anyLong(), contains("Auto-released"), isNull());
        }

        @Test
        @DisplayName("no expired appointments does nothing")
        void noExpired_doesNothing() {
            when(appointmentRepository.findByStatusAndExpiresAtBefore(eq(AppointmentStatus.CREATED), any(LocalDateTime.class)))
                    .thenReturn(Collections.emptyList());

            appointmentService.autoReleaseExpiredAppointments();

            verify(appointmentRepository, never()).save(any(Appointment.class));
            verify(slotRepository, never()).save(any(AppointmentSlot.class));
            verify(auditService, never()).log(any(), any(), any(), any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("slot not found for expired appointment still cancels appointment")
        void slotNotFound_stillCancels() {
            Appointment expired = buildAppointment(1L, 10L, 1L, 100L, AppointmentStatus.CREATED);
            expired.setExpiresAt(LocalDateTime.now().minusMinutes(5));

            when(appointmentRepository.findByStatusAndExpiresAtBefore(eq(AppointmentStatus.CREATED), any(LocalDateTime.class)))
                    .thenReturn(List.of(expired));
            when(slotRepository.findById(10L)).thenReturn(Optional.empty());

            appointmentService.autoReleaseExpiredAppointments();

            verify(appointmentRepository).save(appointmentCaptor.capture());
            assertEquals(AppointmentStatus.CANCELED, appointmentCaptor.getValue().getStatus());
            // Slot not saved since it wasn't found
            verify(slotRepository, never()).save(any(AppointmentSlot.class));
        }
    }

    // =========================================================================
    // 10. enforceOwnerOrPrivileged — role coverage
    // =========================================================================

    @Nested
    @DisplayName("enforceOwnerOrPrivileged (via getAppointment)")
    class EnforceOwnerOrPrivileged {

        private Appointment appt;

        @BeforeEach
        void setUp() {
            appt = buildAppointment(1L, 10L, 1L, 100L, AppointmentStatus.CREATED);
            when(appointmentRepository.findById(1L)).thenReturn(Optional.of(appt));
        }

        @Test
        @DisplayName("owner access succeeds regardless of role")
        void owner_ok() {
            assertDoesNotThrow(() -> appointmentService.getAppointment(1L, 100L, "PATIENT"));
        }

        @Test
        @DisplayName("ADMIN role grants access")
        void adminRole_ok() {
            assertDoesNotThrow(() -> appointmentService.getAppointment(1L, 999L, "ADMIN"));
        }

        @Test
        @DisplayName("DISPATCHER role grants access")
        void dispatcherRole_ok() {
            assertDoesNotThrow(() -> appointmentService.getAppointment(1L, 999L, "DISPATCHER"));
        }

        @Test
        @DisplayName("REVIEWER role grants access")
        void reviewerRole_ok() {
            assertDoesNotThrow(() -> appointmentService.getAppointment(1L, 999L, "REVIEWER"));
        }

        @Test
        @DisplayName("SERVICE_STAFF role grants access")
        void serviceStaffRole_ok() {
            assertDoesNotThrow(() -> appointmentService.getAppointment(1L, 999L, "SERVICE_STAFF"));
        }

        @Test
        @DisplayName("FINANCE role does NOT grant access (not privileged)")
        void financeRole_throws() {
            BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                    () -> appointmentService.getAppointment(1L, 999L, "FINANCE"));
            assertTrue(ex.getMessage().contains("Not authorized"));
        }

        @Test
        @DisplayName("PATIENT role without ownership throws")
        void patientRole_nonOwner_throws() {
            BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                    () -> appointmentService.getAppointment(1L, 999L, "PATIENT"));
            assertTrue(ex.getMessage().contains("Not authorized"));
        }

        @Test
        @DisplayName("null role without ownership throws")
        void nullRole_nonOwner_throws() {
            BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                    () -> appointmentService.getAppointment(1L, 999L, null));
            assertTrue(ex.getMessage().contains("Not authorized"));
        }
    }
}
