package com.anju.appointment.property;

import com.anju.appointment.BaseIntegrationTest;
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

import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PropertyControllerTest extends BaseIntegrationTest {

    @Autowired
    private PropertyRepository propertyRepository;
    @Autowired
    private AppointmentSlotRepository slotRepository;
    @Autowired
    private AppointmentRepository appointmentRepository;

    @BeforeEach
    void setUp() {
        appointmentRepository.deleteAll();
        slotRepository.deleteAll();
        propertyRepository.deleteAll();
    }

    @Test
    void crud_operations_work() throws Exception {
        // Create
        String createBody = "{\"name\":\"Room A\",\"type\":\"ROOM\",\"address\":\"Floor 1\"," +
                "\"description\":\"Consultation room\",\"capacity\":5}";

        String response = mockMvc.perform(post("/api/properties")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("Room A")))
                .andExpect(jsonPath("$.status", is("ACTIVE")))
                .andReturn().getResponse().getContentAsString();

        Long propertyId = objectMapper.readTree(response).get("id").asLong();

        // Read
        mockMvc.perform(get("/api/properties/" + propertyId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("Room A")));

        // Update
        mockMvc.perform(put("/api/properties/" + propertyId)
                        .header("Authorization", "Bearer " + dispatcherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Room B\",\"type\":\"ROOM\",\"capacity\":10}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("Room B")))
                .andExpect(jsonPath("$.capacity", is(10)));

        // List
        mockMvc.perform(get("/api/properties")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements", is(1)));

        // Delete (soft)
        mockMvc.perform(delete("/api/properties/" + propertyId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        Property deleted = propertyRepository.findById(propertyId).orElseThrow();
        assertEquals(PropertyStatus.INACTIVE, deleted.getStatus());
    }

    @Test
    void deleteProperty_withActiveAppointments_returns409() throws Exception {
        Property property = new Property();
        property.setName("Busy Room");
        property.setType("ROOM");
        property.setCapacity(3);
        property.setStatus(PropertyStatus.ACTIVE);
        property = propertyRepository.save(property);

        AppointmentSlot slot = new AppointmentSlot();
        slot.setPropertyId(property.getId());
        slot.setDate(LocalDate.now().plusDays(5));
        slot.setStartTime(LocalTime.of(10, 0));
        slot.setEndTime(LocalTime.of(10, 30));
        slot.setDuration(30);
        slot.setCapacity(3);
        slot = slotRepository.save(slot);

        Appointment appointment = new Appointment();
        appointment.setSlotId(slot.getId());
        appointment.setPropertyId(property.getId());
        appointment.setUserId(adminUser.getId());
        appointment.setPatientName("Patient");
        appointment.setPatientPhone("13800138000");
        appointment.setServiceType("GENERAL");
        appointment.setStatus(AppointmentStatus.CONFIRMED);
        appointment.setExpiresAt(LocalDateTime.now().plusMinutes(15));
        appointment.setIdempotencyKey(UUID.randomUUID().toString());
        appointmentRepository.save(appointment);

        mockMvc.perform(delete("/api/properties/" + property.getId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("active appointments")));
    }

    @Test
    void createProperty_financeRole_returns403() throws Exception {
        String body = "{\"name\":\"Room\",\"type\":\"ROOM\",\"capacity\":3}";

        mockMvc.perform(post("/api/properties")
                        .header("Authorization", "Bearer " + financeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    void createProperty_dispatcherRole_succeeds() throws Exception {
        String body = "{\"name\":\"Room\",\"type\":\"ROOM\",\"address\":\"Addr\",\"capacity\":3}";

        mockMvc.perform(post("/api/properties")
                        .header("Authorization", "Bearer " + dispatcherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("Room")));
    }

    @Test
    void updateProperty_partialPayload_succeeds() throws Exception {
        Property property = new Property();
        property.setName("Room A");
        property.setType("ROOM");
        property.setAddress("Floor 1");
        property.setCapacity(3);
        property.setStatus(PropertyStatus.ACTIVE);
        property = propertyRepository.save(property);

        mockMvc.perform(put("/api/properties/" + property.getId())
                        .header("Authorization", "Bearer " + dispatcherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"Updated only\",\"minBookingLeadHours\":4}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("Room A")))
                .andExpect(jsonPath("$.description", is("Updated only")))
                .andExpect(jsonPath("$.minBookingLeadHours", is(4)));
    }
}
