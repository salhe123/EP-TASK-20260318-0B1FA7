package com.anju.appointment.appointment;

import com.anju.appointment.BaseIntegrationTest;
import com.anju.appointment.appointment.entity.Appointment;
import com.anju.appointment.appointment.entity.AppointmentSlot;
import com.anju.appointment.appointment.entity.AppointmentStatus;
import com.anju.appointment.appointment.repository.AppointmentRepository;
import com.anju.appointment.appointment.repository.AppointmentSlotRepository;
import com.anju.appointment.appointment.service.AppointmentService;
import com.anju.appointment.property.entity.Property;
import com.anju.appointment.property.entity.ComplianceStatus;
import com.anju.appointment.property.entity.PropertyStatus;
import com.anju.appointment.property.repository.PropertyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;

import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AppointmentControllerTest extends BaseIntegrationTest {

    @Autowired
    private PropertyRepository propertyRepository;

    @Autowired
    private AppointmentSlotRepository slotRepository;

    @Autowired
    private AppointmentRepository appointmentRepository;

    @Autowired
    private AppointmentService appointmentService;

    private Property property;

    @BeforeEach
    void setUp() {
        appointmentRepository.deleteAll();
        slotRepository.deleteAll();
        propertyRepository.deleteAll();

        property = new Property();
        property.setName("Test Room");
        property.setType("ROOM");
        property.setAddress("Test Address");
        property.setCapacity(3);
        property.setStatus(PropertyStatus.ACTIVE);
        property = propertyRepository.save(property);
    }

    // --- Slot Generation ---

    @Test
    void generateSlots_createsCorrectNumberOfSlots() throws Exception {
        LocalDate futureDate = LocalDate.now().plusDays(5);
        String body = String.format(
                "{\"propertyId\":%d,\"date\":\"%s\",\"slotDuration\":30,\"startTime\":\"08:00\",\"endTime\":\"17:00\"}",
                property.getId(), futureDate);

        mockMvc.perform(post("/api/appointments/slots/generate")
                        .header("Authorization", "Bearer " + dispatcherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slotsGenerated", is(18)))
                .andExpect(jsonPath("$.slotDuration", is(30)));
    }

    @Test
    void generateSlots_duplicateDate_returns409() throws Exception {
        LocalDate futureDate = LocalDate.now().plusDays(5);
        String body = String.format(
                "{\"propertyId\":%d,\"date\":\"%s\",\"slotDuration\":30,\"startTime\":\"08:00\",\"endTime\":\"17:00\"}",
                property.getId(), futureDate);

        mockMvc.perform(post("/api/appointments/slots/generate")
                .header("Authorization", "Bearer " + dispatcherToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));

        mockMvc.perform(post("/api/appointments/slots/generate")
                        .header("Authorization", "Bearer " + dispatcherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Slots already exist for this property and date"));
    }

    // --- Booking ---

    @Test
    void bookAppointment_success() throws Exception {
        AppointmentSlot slot = createFutureSlot(3);
        String body = bookingJson(slot.getId(), UUID.randomUUID().toString());

        mockMvc.perform(post("/api/appointments")
                        .header("Authorization", "Bearer " + dispatcherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("CREATED")))
                .andExpect(jsonPath("$.slotId", is(slot.getId().intValue())));
    }

    @Test
    void bookAppointment_slotAtCapacity_returns409() throws Exception {
        AppointmentSlot slot = createFutureSlot(1);
        // Fill the slot
        bookSlot(slot.getId());

        String body = bookingJson(slot.getId(), UUID.randomUUID().toString());
        mockMvc.perform(post("/api/appointments")
                        .header("Authorization", "Bearer " + dispatcherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Slot is at full capacity"));
    }

    @Test
    void bookAppointment_lessThan2HoursAdvance_returns409() throws Exception {
        // Create a slot that starts in 1 hour
        AppointmentSlot slot = new AppointmentSlot();
        slot.setPropertyId(property.getId());
        slot.setDate(LocalDate.now());
        slot.setStartTime(LocalTime.now().plusMinutes(30));
        slot.setEndTime(LocalTime.now().plusMinutes(60));
        slot.setDuration(30);
        slot.setCapacity(3);
        slot = slotRepository.save(slot);

        String body = bookingJson(slot.getId(), UUID.randomUUID().toString());
        mockMvc.perform(post("/api/appointments")
                        .header("Authorization", "Bearer " + dispatcherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("at least 2 hours")));
    }

    @Test
    void bookAppointment_moreThan14DaysAhead_returns409() throws Exception {
        AppointmentSlot slot = new AppointmentSlot();
        slot.setPropertyId(property.getId());
        slot.setDate(LocalDate.now().plusDays(20));
        slot.setStartTime(LocalTime.of(10, 0));
        slot.setEndTime(LocalTime.of(10, 30));
        slot.setDuration(30);
        slot.setCapacity(3);
        slot = slotRepository.save(slot);

        String body = bookingJson(slot.getId(), UUID.randomUUID().toString());
        mockMvc.perform(post("/api/appointments")
                        .header("Authorization", "Bearer " + dispatcherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("14 days")));
    }

    @Test
    void bookAppointment_respectsPropertyMinLeadHours() throws Exception {
        property.setMinBookingLeadHours(6);
        propertyRepository.save(property);

        AppointmentSlot slot = createSlot(LocalDate.now().plusDays(1), LocalTime.now().plusHours(4).withMinute(0), 3);
        String body = bookingJson(slot.getId(), UUID.randomUUID().toString());

        mockMvc.perform(post("/api/appointments")
                        .header("Authorization", "Bearer " + dispatcherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("at least 6 hours")));
    }

    @Test
    void bookAppointment_respectsPropertyMaxLeadDays() throws Exception {
        property.setMaxBookingLeadDays(3);
        propertyRepository.save(property);

        AppointmentSlot slot = createSlot(LocalDate.now().plusDays(5), LocalTime.of(10, 0), 3);
        String body = bookingJson(slot.getId(), UUID.randomUUID().toString());

        mockMvc.perform(post("/api/appointments")
                        .header("Authorization", "Bearer " + dispatcherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("more than 3 days")));
    }

    @Test
    void bookAppointment_nonCompliantProperty_returns409() throws Exception {
        property.setComplianceStatus(ComplianceStatus.NON_COMPLIANT);
        propertyRepository.save(property);

        AppointmentSlot slot = createFutureSlot(3);
        String body = bookingJson(slot.getId(), UUID.randomUUID().toString());

        mockMvc.perform(post("/api/appointments")
                        .header("Authorization", "Bearer " + dispatcherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("non-compliant")));
    }

    @Test
    void bookAppointment_duplicateIdempotencyKey_returnsExisting() throws Exception {
        AppointmentSlot slot = createFutureSlot(3);
        String idempotencyKey = UUID.randomUUID().toString();
        String body = bookingJson(slot.getId(), idempotencyKey);

        // First booking
        mockMvc.perform(post("/api/appointments")
                        .header("Authorization", "Bearer " + dispatcherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        // Same idempotency key — should return existing, not fail
        mockMvc.perform(post("/api/appointments")
                        .header("Authorization", "Bearer " + dispatcherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idempotencyKey", is(idempotencyKey)));

        // Verify only one appointment was created
        assertEquals(1, appointmentRepository.count());
    }

    // --- Lifecycle ---

    @Test
    void lifecycle_created_confirmed_completed() throws Exception {
        AppointmentSlot slot = createFutureSlot(3);
        Appointment appointment = createAppointment(slot, AppointmentStatus.CREATED);

        // Confirm
        mockMvc.perform(put("/api/appointments/" + appointment.getId() + "/confirm")
                        .header("Authorization", "Bearer " + dispatcherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("CONFIRMED")));

        // Complete
        mockMvc.perform(put("/api/appointments/" + appointment.getId() + "/complete")
                        .header("Authorization", "Bearer " + dispatcherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"completionNotes\":\"Done\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("COMPLETED")));
    }

    @Test
    void autoRelease_expiredCreatedAppointment_isCanceled() throws Exception {
        AppointmentSlot slot = createFutureSlot(3);

        // Create appointment with already-expired expiresAt
        Appointment appointment = new Appointment();
        appointment.setSlotId(slot.getId());
        appointment.setPropertyId(property.getId());
        appointment.setUserId(dispatcherUser.getId());
        appointment.setPatientName("Test Patient");
        appointment.setPatientPhone("13800138000");
        appointment.setServiceType("GENERAL");
        appointment.setStatus(AppointmentStatus.CREATED);
        appointment.setExpiresAt(LocalDateTime.now().minusMinutes(5));
        appointment.setIdempotencyKey(UUID.randomUUID().toString());
        appointment = appointmentRepository.save(appointment);

        // Run auto-release
        appointmentService.autoReleaseExpiredAppointments();

        Appointment released = appointmentRepository.findById(appointment.getId()).orElseThrow();
        assertEquals(AppointmentStatus.CANCELED, released.getStatus());
        assertEquals("Auto-released: not confirmed within 15 minutes", released.getCancelReason());

        // Verify slot capacity freed
        AppointmentSlot updatedSlot = slotRepository.findById(slot.getId()).orElseThrow();
        assertEquals(0, updatedSlot.getBookedCount());
    }

    // --- Cancellation ---

    @Test
    void cancel_oneHourOrMoreBefore_canceledDirectly() throws Exception {
        // Slot far in the future (>1 hour)
        AppointmentSlot slot = createFutureSlot(3);
        Appointment appointment = createAppointment(slot, AppointmentStatus.CONFIRMED);

        mockMvc.perform(put("/api/appointments/" + appointment.getId() + "/cancel")
                        .header("Authorization", "Bearer " + dispatcherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Patient requested\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("CANCELED")));

        // Verify slot capacity freed
        AppointmentSlot updatedSlot = slotRepository.findById(slot.getId()).orElseThrow();
        assertEquals(0, updatedSlot.getBookedCount());
    }

    @Test
    void cancel_lessThanOneHourBefore_becomesException() throws Exception {
        // Create a slot starting in 30 minutes
        AppointmentSlot slot = new AppointmentSlot();
        slot.setPropertyId(property.getId());
        slot.setDate(LocalDate.now());
        slot.setStartTime(LocalTime.now().plusMinutes(30));
        slot.setEndTime(LocalTime.now().plusMinutes(60));
        slot.setDuration(30);
        slot.setCapacity(3);
        slot.setBookedCount(1);
        slot = slotRepository.save(slot);

        Appointment appointment = new Appointment();
        appointment.setSlotId(slot.getId());
        appointment.setPropertyId(property.getId());
        appointment.setUserId(dispatcherUser.getId());
        appointment.setPatientName("Test Patient");
        appointment.setPatientPhone("13800138000");
        appointment.setServiceType("GENERAL");
        appointment.setStatus(AppointmentStatus.CONFIRMED);
        appointment.setExpiresAt(LocalDateTime.now().plusMinutes(15));
        appointment.setIdempotencyKey(UUID.randomUUID().toString());
        appointment = appointmentRepository.save(appointment);

        mockMvc.perform(put("/api/appointments/" + appointment.getId() + "/cancel")
                        .header("Authorization", "Bearer " + dispatcherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Emergency\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("EXCEPTION")));

        // Slot capacity NOT freed yet
        AppointmentSlot updatedSlot = slotRepository.findById(slot.getId()).orElseThrow();
        assertEquals(1, updatedSlot.getBookedCount());
    }

    @Test
    void approveCancel_exceptionToCancel() throws Exception {
        AppointmentSlot slot = createFutureSlot(3);
        slot.setBookedCount(1);
        slot = slotRepository.save(slot);

        Appointment appointment = new Appointment();
        appointment.setSlotId(slot.getId());
        appointment.setPropertyId(property.getId());
        appointment.setUserId(dispatcherUser.getId());
        appointment.setPatientName("Test Patient");
        appointment.setPatientPhone("13800138000");
        appointment.setServiceType("GENERAL");
        appointment.setStatus(AppointmentStatus.EXCEPTION);
        appointment.setCancelReason("Late cancel");
        appointment.setExpiresAt(LocalDateTime.now().plusMinutes(15));
        appointment.setIdempotencyKey(UUID.randomUUID().toString());
        appointment = appointmentRepository.save(appointment);

        mockMvc.perform(put("/api/appointments/" + appointment.getId() + "/approve-cancel")
                        .header("Authorization", "Bearer " + reviewerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Approved: emergency\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("CANCELED")));

        // Slot capacity freed after approval
        AppointmentSlot updatedSlot = slotRepository.findById(slot.getId()).orElseThrow();
        assertEquals(0, updatedSlot.getBookedCount());
    }

    // --- Reschedule ---

    @Test
    void reschedule_firstAndSecondTime_succeeds() throws Exception {
        AppointmentSlot slot1 = createFutureSlot(3);
        AppointmentSlot slot2 = createFutureSlot(3);
        AppointmentSlot slot3 = createFutureSlot(3);
        Appointment appointment = createAppointment(slot1, AppointmentStatus.CONFIRMED);

        // 1st reschedule
        mockMvc.perform(put("/api/appointments/" + appointment.getId() + "/reschedule")
                        .header("Authorization", "Bearer " + dispatcherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newSlotId\":" + slot2.getId() + ",\"reason\":\"Conflict\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rescheduleCount", is(1)));

        // 2nd reschedule
        mockMvc.perform(put("/api/appointments/" + appointment.getId() + "/reschedule")
                        .header("Authorization", "Bearer " + dispatcherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newSlotId\":" + slot3.getId() + ",\"reason\":\"Another conflict\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rescheduleCount", is(2)));
    }

    @Test
    void reschedule_thirdTime_returns409() throws Exception {
        AppointmentSlot slot1 = createFutureSlot(3);
        Appointment appointment = createAppointment(slot1, AppointmentStatus.CONFIRMED);
        appointment.setRescheduleCount(2);
        appointment = appointmentRepository.save(appointment);

        AppointmentSlot newSlot = createFutureSlot(3);

        mockMvc.perform(put("/api/appointments/" + appointment.getId() + "/reschedule")
                        .header("Authorization", "Bearer " + dispatcherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newSlotId\":" + newSlot.getId() + ",\"reason\":\"Again\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("Maximum reschedule limit")));
    }

    @Test
    void canceledAppointment_freesSlotCapacity() throws Exception {
        AppointmentSlot slot = createFutureSlot(1);
        Appointment appointment = createAppointment(slot, AppointmentStatus.CREATED);

        // Slot should be booked
        AppointmentSlot bookedSlot = slotRepository.findById(slot.getId()).orElseThrow();
        assertEquals(1, bookedSlot.getBookedCount());

        // Cancel
        mockMvc.perform(put("/api/appointments/" + appointment.getId() + "/cancel")
                        .header("Authorization", "Bearer " + dispatcherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Not needed\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("CANCELED")));

        // Slot should be freed
        AppointmentSlot freedSlot = slotRepository.findById(slot.getId()).orElseThrow();
        assertEquals(0, freedSlot.getBookedCount());
    }

    // --- Helpers ---

    private AppointmentSlot createFutureSlot(int capacity) {
        return createSlot(LocalDate.now().plusDays(5), LocalTime.of(10, 0), capacity);
    }

    private AppointmentSlot createSlot(LocalDate date, LocalTime startTime, int capacity) {
        AppointmentSlot slot = new AppointmentSlot();
        slot.setPropertyId(property.getId());
        slot.setDate(date);
        slot.setStartTime(startTime);
        slot.setEndTime(startTime.plusMinutes(30));
        slot.setDuration(30);
        slot.setCapacity(capacity);
        return slotRepository.save(slot);
    }

    private Appointment createAppointment(AppointmentSlot slot, AppointmentStatus status) {
        slot.setBookedCount(slot.getBookedCount() + 1);
        slotRepository.save(slot);

        Appointment appointment = new Appointment();
        appointment.setSlotId(slot.getId());
        appointment.setPropertyId(property.getId());
        appointment.setUserId(dispatcherUser.getId());
        appointment.setPatientName("Test Patient");
        appointment.setPatientPhone("13800138000");
        appointment.setServiceType("GENERAL_CONSULTATION");
        appointment.setStatus(status);
        appointment.setExpiresAt(LocalDateTime.now().plusMinutes(15));
        appointment.setIdempotencyKey(UUID.randomUUID().toString());
        return appointmentRepository.save(appointment);
    }

    private void bookSlot(Long slotId) {
        AppointmentSlot slot = slotRepository.findById(slotId).orElseThrow();
        slot.setBookedCount(slot.getBookedCount() + 1);
        slotRepository.save(slot);

        Appointment appointment = new Appointment();
        appointment.setSlotId(slotId);
        appointment.setPropertyId(property.getId());
        appointment.setUserId(dispatcherUser.getId());
        appointment.setPatientName("Existing Patient");
        appointment.setPatientPhone("13900139000");
        appointment.setServiceType("GENERAL");
        appointment.setStatus(AppointmentStatus.CREATED);
        appointment.setExpiresAt(LocalDateTime.now().plusMinutes(15));
        appointment.setIdempotencyKey(UUID.randomUUID().toString());
        appointmentRepository.save(appointment);
    }

    private String bookingJson(Long slotId, String idempotencyKey) {
        return String.format(
                "{\"slotId\":%d,\"propertyId\":%d,\"patientName\":\"Li Wei\"," +
                "\"patientPhone\":\"13800138000\",\"serviceType\":\"GENERAL_CONSULTATION\"," +
                "\"notes\":\"Test\",\"idempotencyKey\":\"%s\"}",
                slotId, property.getId(), idempotencyKey);
    }
}
