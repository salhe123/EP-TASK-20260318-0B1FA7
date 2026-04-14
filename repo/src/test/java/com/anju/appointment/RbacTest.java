package com.anju.appointment;

import com.anju.appointment.appointment.entity.Appointment;
import com.anju.appointment.appointment.entity.AppointmentSlot;
import com.anju.appointment.appointment.entity.AppointmentStatus;
import com.anju.appointment.appointment.repository.AppointmentRepository;
import com.anju.appointment.appointment.repository.AppointmentSlotRepository;
import com.anju.appointment.property.entity.Property;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RbacTest extends BaseIntegrationTest {

    @Autowired
    private PropertyRepository propertyRepository;
    @Autowired
    private AppointmentSlotRepository slotRepository;
    @Autowired
    private AppointmentRepository appointmentRepository;

    private Property property;

    @BeforeEach
    void setUp() {
        appointmentRepository.deleteAll();
        slotRepository.deleteAll();
        propertyRepository.deleteAll();

        property = new Property();
        property.setName("RBAC Test Room");
        property.setType("ROOM");
        property.setCapacity(3);
        property.setStatus(PropertyStatus.ACTIVE);
        property = propertyRepository.save(property);
    }

    @Test
    void admin_canAccessEverything() throws Exception {
        // Admin can list users
        mockMvc.perform(get("/api/admin/users")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        // Admin can list properties
        mockMvc.perform(get("/api/properties")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        // Admin can access financial
        mockMvc.perform(get("/api/financial/transactions")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        // Admin can access audit
        mockMvc.perform(get("/api/audit/logs")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }

    @Test
    void finance_cannotCreateAppointments() throws Exception {
        // FINANCE can book (any authenticated user can), but cannot generate slots
        mockMvc.perform(post("/api/appointments/slots/generate")
                        .header("Authorization", "Bearer " + financeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format(
                                "{\"propertyId\":%d,\"date\":\"%s\",\"slotDuration\":30," +
                                "\"startTime\":\"08:00\",\"endTime\":\"17:00\"}",
                                property.getId(), LocalDate.now().plusDays(5))))
                .andExpect(status().isForbidden());

        // FINANCE cannot confirm appointments
        AppointmentSlot slot = createSlot();
        Appointment appointment = createAppointment(slot);

        mockMvc.perform(put("/api/appointments/" + appointment.getId() + "/confirm")
                        .header("Authorization", "Bearer " + financeToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void dispatcher_cannotRecordTransactions() throws Exception {
        mockMvc.perform(post("/api/financial/transactions")
                        .header("Authorization", "Bearer " + dispatcherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"appointmentId\":1,\"type\":\"SERVICE_FEE\"," +
                                "\"amount\":100,\"idempotencyKey\":\"test\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/financial/transactions")
                        .header("Authorization", "Bearer " + dispatcherToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void reviewer_canApproveCancellations() throws Exception {
        AppointmentSlot slot = createSlot();
        slot.setBookedCount(1);
        slot = slotRepository.save(slot);

        Appointment appointment = new Appointment();
        appointment.setSlotId(slot.getId());
        appointment.setPropertyId(property.getId());
        appointment.setUserId(dispatcherUser.getId());
        appointment.setPatientName("Patient");
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
                        .content("{\"reason\":\"Approved\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELED"));
    }

    private AppointmentSlot createSlot() {
        AppointmentSlot slot = new AppointmentSlot();
        slot.setPropertyId(property.getId());
        slot.setDate(LocalDate.now().plusDays(5));
        slot.setStartTime(LocalTime.of(10, 0));
        slot.setEndTime(LocalTime.of(10, 30));
        slot.setDuration(30);
        slot.setCapacity(3);
        return slotRepository.save(slot);
    }

    private Appointment createAppointment(AppointmentSlot slot) {
        Appointment appointment = new Appointment();
        appointment.setSlotId(slot.getId());
        appointment.setPropertyId(property.getId());
        appointment.setUserId(dispatcherUser.getId());
        appointment.setPatientName("Patient");
        appointment.setPatientPhone("13800138000");
        appointment.setServiceType("GENERAL");
        appointment.setStatus(AppointmentStatus.CREATED);
        appointment.setExpiresAt(LocalDateTime.now().plusMinutes(15));
        appointment.setIdempotencyKey(UUID.randomUUID().toString());
        return appointmentRepository.save(appointment);
    }
}
