package com.anju.appointment;

import com.anju.appointment.appointment.entity.Appointment;
import com.anju.appointment.appointment.entity.AppointmentSlot;
import com.anju.appointment.appointment.entity.AppointmentStatus;
import com.anju.appointment.appointment.repository.AppointmentRepository;
import com.anju.appointment.appointment.repository.AppointmentSlotRepository;
import com.anju.appointment.audit.entity.AuditLog;
import com.anju.appointment.audit.repository.AuditLogRepository;
import com.anju.appointment.auth.entity.RefreshToken;
import com.anju.appointment.auth.entity.Role;
import com.anju.appointment.auth.entity.User;
import com.anju.appointment.file.repository.FileRecordRepository;
import com.anju.appointment.property.entity.Property;
import com.anju.appointment.property.entity.PropertyStatus;
import com.anju.appointment.property.repository.PropertyRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SecurityAuthorizationTest extends BaseIntegrationTest {

    @Autowired
    private PropertyRepository propertyRepository;
    @Autowired
    private AppointmentSlotRepository slotRepository;
    @Autowired
    private AppointmentRepository appointmentRepository;
    @Autowired
    private FileRecordRepository fileRecordRepository;
    @Autowired
    private AuditLogRepository auditLogRepository;

    @Value("${app.storage.path}")
    private String storagePath;

    private Property property;

    @BeforeEach
    void setUp() {
        auditLogRepository.deleteAll();
        fileRecordRepository.deleteAll();
        appointmentRepository.deleteAll();
        slotRepository.deleteAll();
        propertyRepository.deleteAll();

        property = new Property();
        property.setName("Auth Test Room");
        property.setType("ROOM");
        property.setAddress("Test Address");
        property.setCapacity(3);
        property.setStatus(PropertyStatus.ACTIVE);
        property.setComplianceStatus(com.anju.appointment.property.entity.ComplianceStatus.COMPLIANT);
        property = propertyRepository.save(property);
    }

    @AfterEach
    void cleanUp() throws IOException {
        Path storage = Paths.get(storagePath).toAbsolutePath().normalize();
        if (Files.exists(storage)) {
            Files.walkFileTree(storage, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    Files.delete(dir);
                    return FileVisitResult.CONTINUE;
                }
            });
        }
    }

    // --- Appointment object-level authorization ---

    @Test
    void getAppointment_ownerCanAccess() throws Exception {
        Appointment appointment = createAppointment(dispatcherUser.getId());

        mockMvc.perform(get("/api/appointments/" + appointment.getId())
                        .header("Authorization", "Bearer " + dispatcherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(appointment.getId().intValue())));
    }

    @Test
    void getAppointment_adminCanAccess() throws Exception {
        Appointment appointment = createAppointment(dispatcherUser.getId());

        mockMvc.perform(get("/api/appointments/" + appointment.getId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }

    @Test
    void getAppointment_nonOwnerNonPrivileged_returns403() throws Exception {
        Appointment appointment = createAppointment(dispatcherUser.getId());

        mockMvc.perform(get("/api/appointments/" + appointment.getId())
                        .header("Authorization", "Bearer " + financeToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("Not authorized")));
    }

    @Test
    void rescheduleAppointment_nonOwnerNonPrivileged_returns403() throws Exception {
        AppointmentSlot slot = createFutureSlot();
        Appointment appointment = createAppointmentWithSlot(slot, dispatcherUser.getId());
        AppointmentSlot newSlot = createFutureSlot();

        mockMvc.perform(put("/api/appointments/" + appointment.getId() + "/reschedule")
                        .header("Authorization", "Bearer " + financeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newSlotId\":" + newSlot.getId() + ",\"reason\":\"Conflict\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("Not authorized")));
    }

    @Test
    void rescheduleAppointment_ownerCanReschedule() throws Exception {
        AppointmentSlot slot = createFutureSlot();
        Appointment appointment = createAppointmentWithSlot(slot, dispatcherUser.getId());
        AppointmentSlot newSlot = createFutureSlot();

        mockMvc.perform(put("/api/appointments/" + appointment.getId() + "/reschedule")
                        .header("Authorization", "Bearer " + dispatcherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newSlotId\":" + newSlot.getId() + ",\"reason\":\"Conflict\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rescheduleCount", is(1)));
    }

    // --- File object-level authorization ---

    @Test
    void listFiles_nonPrivilegedUser_seesOnlyOwnFiles() throws Exception {
        // Finance user uploads a file
        MockMultipartFile file = new MockMultipartFile(
                "file", "finance-doc.pdf", "application/pdf", "content".getBytes());
        mockMvc.perform(multipart("/api/files/upload")
                .file(file)
                .param("module", "FINANCIAL")
                .header("Authorization", "Bearer " + financeToken));

        // Admin uploads a file
        MockMultipartFile file2 = new MockMultipartFile(
                "file", "admin-doc.pdf", "application/pdf", "content2".getBytes());
        mockMvc.perform(multipart("/api/files/upload")
                .file(file2)
                .param("module", "PROPERTY")
                .header("Authorization", "Bearer " + adminToken));

        // Finance user should only see their own file
        mockMvc.perform(get("/api/files")
                        .header("Authorization", "Bearer " + financeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements", is(1)));

        // Admin sees all files
        mockMvc.perform(get("/api/files")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements", is(2)));
    }

    @Test
    void downloadFile_nonOwnerNonPrivileged_returns403() throws Exception {
        // Admin uploads a file
        MockMultipartFile file = new MockMultipartFile(
                "file", "secret.pdf", "application/pdf", "secret content".getBytes());
        String response = mockMvc.perform(multipart("/api/files/upload")
                        .file(file)
                        .param("module", "PROPERTY")
                        .header("Authorization", "Bearer " + adminToken))
                .andReturn().getResponse().getContentAsString();

        Long fileId = objectMapper.readTree(response).get("id").asLong();

        // Finance user tries to download — should be denied
        mockMvc.perform(get("/api/files/" + fileId + "/download")
                        .header("Authorization", "Bearer " + financeToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("Not authorized")));
    }

    @Test
    void getFileRecord_nonOwnerNonPrivileged_returns403() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "doc.pdf", "application/pdf", "content".getBytes());
        String response = mockMvc.perform(multipart("/api/files/upload")
                        .file(file)
                        .param("module", "APPOINTMENT")
                        .header("Authorization", "Bearer " + adminToken))
                .andReturn().getResponse().getContentAsString();

        Long fileId = objectMapper.readTree(response).get("id").asLong();

        mockMvc.perform(get("/api/files/" + fileId)
                        .header("Authorization", "Bearer " + financeToken))
                .andExpect(status().isForbidden());
    }

    // --- Refresh token flow ---

    @Test
    void refreshToken_worksWithoutAccessToken() throws Exception {
        // Clear any existing tokens to avoid unique constraint collision
        refreshTokenRepository.deleteAll();

        // Login to get refresh token from Set-Cookie header
        var loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"Admin123\"}"))
                .andExpect(status().isOk())
                .andReturn();

        // Extract refresh token from Set-Cookie header
        String refreshTokenValue = null;
        for (String header : loginResult.getResponse().getHeaders("Set-Cookie")) {
            if (header.startsWith("refreshToken=")) {
                refreshTokenValue = header.split(";")[0].substring("refreshToken=".length());
                break;
            }
        }
        assertFalse(refreshTokenValue == null || refreshTokenValue.isEmpty(),
                "Expected refreshToken in Set-Cookie header");

        Cookie cookieToSend = new Cookie("refreshToken", refreshTokenValue);

        // Use refresh token cookie without any access token header
        mockMvc.perform(post("/api/auth/refresh")
                        .cookie(cookieToSend))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken", notNullValue()));
    }

    // --- Overlapping booking conflict detection ---

    @Test
    void bookAppointment_overlappingSlot_returns409() throws Exception {
        AppointmentSlot slot1 = createFutureSlot();
        // Book first appointment
        String body1 = bookingJson(slot1.getId(), UUID.randomUUID().toString());
        mockMvc.perform(post("/api/appointments")
                        .header("Authorization", "Bearer " + dispatcherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body1))
                .andExpect(status().isOk());

        // Create another slot at the same time (different capacity)
        AppointmentSlot slot2 = new AppointmentSlot();
        slot2.setPropertyId(property.getId());
        slot2.setDate(slot1.getDate());
        slot2.setStartTime(slot1.getStartTime());
        slot2.setEndTime(slot1.getEndTime());
        slot2.setDuration(30);
        slot2.setCapacity(3);
        slot2 = slotRepository.save(slot2);

        // Same user tries to book overlapping slot
        String body2 = bookingJson(slot2.getId(), UUID.randomUUID().toString());
        mockMvc.perform(post("/api/appointments")
                        .header("Authorization", "Bearer " + dispatcherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body2))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("overlapping appointment")));
    }

    // --- Audit logging across modules ---

    @Test
    void propertyCreate_generatesAuditLog() throws Exception {
        String body = "{\"name\":\"Audited Room\",\"type\":\"ROOM\",\"address\":\"Addr\",\"capacity\":3}";
        mockMvc.perform(post("/api/properties")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        List<AuditLog> logs = auditLogRepository.findAll();
        assertTrue(logs.stream().anyMatch(l ->
                "PROPERTY".equals(l.getModule()) && "CREATE".equals(l.getOperation())));
    }

    @Test
    void financialTransaction_generatesAuditLog() throws Exception {
        AppointmentSlot slot = createFutureSlot();
        Appointment appointment = createAppointmentWithSlot(slot, financeUser.getId());

        String body = String.format(
                "{\"appointmentId\":%d,\"type\":\"SERVICE_FEE\",\"amount\":100.00," +
                "\"description\":\"Fee\",\"idempotencyKey\":\"%s\"}",
                appointment.getId(), UUID.randomUUID().toString());

        mockMvc.perform(post("/api/financial/transactions")
                        .header("Authorization", "Bearer " + financeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        List<AuditLog> logs = auditLogRepository.findAll();
        assertTrue(logs.stream().anyMatch(l ->
                "FINANCIAL".equals(l.getModule()) && "CREATE".equals(l.getOperation())
                && "Transaction".equals(l.getEntityType())));
    }

    @Test
    void fileUpload_generatesAuditLog() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "audit-test.pdf", "application/pdf", "content".getBytes());
        mockMvc.perform(multipart("/api/files/upload")
                        .file(file)
                        .param("module", "APPOINTMENT")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        List<AuditLog> logs = auditLogRepository.findAll();
        assertTrue(logs.stream().anyMatch(l ->
                "FILE".equals(l.getModule()) && "CREATE".equals(l.getOperation())));
    }

    @Test
    void userManagement_generatesAuditLog() throws Exception {
        String body = "{\"username\":\"audituser\",\"password\":\"AuditPass1!xx\"," +
                "\"fullName\":\"Audit User\",\"role\":\"FINANCE\"," +
                "\"phone\":\"13800138000\",\"email\":\"audit@test.com\"}";

        mockMvc.perform(post("/api/admin/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        List<AuditLog> logs = auditLogRepository.findAll();
        assertTrue(logs.stream().anyMatch(l ->
                "AUTH".equals(l.getModule()) && "CREATE".equals(l.getOperation())
                && "User".equals(l.getEntityType())));
    }

    // --- Settlement ---

    @Test
    void createSettlement_succeeds() throws Exception {
        AppointmentSlot slot = createFutureSlot();
        Appointment appointment = createAppointmentWithSlot(slot, financeUser.getId());

        // Create a transaction first
        String txnBody = String.format(
                "{\"appointmentId\":%d,\"type\":\"SERVICE_FEE\",\"amount\":500.00," +
                "\"description\":\"Fee\",\"idempotencyKey\":\"%s\"}",
                appointment.getId(), UUID.randomUUID().toString());
        mockMvc.perform(post("/api/financial/transactions")
                .header("Authorization", "Bearer " + financeToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(txnBody));

        String today = LocalDate.now().toString();
        String body = String.format(
                "{\"periodStart\":\"%s\",\"periodEnd\":\"%s\",\"notes\":\"Test settlement\"}",
                today, today);

        mockMvc.perform(post("/api/financial/settlements")
                        .header("Authorization", "Bearer " + financeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("DRAFT")))
                .andExpect(jsonPath("$.netAmount").isNumber());
    }

    // --- Report export ---

    @Test
    void exportDailyReport_returnsCsv() throws Exception {
        String today = LocalDate.now().toString();
        mockMvc.perform(get("/api/financial/reports/daily/export")
                        .header("Authorization", "Bearer " + financeToken)
                        .param("date", today))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .header().string("Content-Disposition",
                                org.hamcrest.Matchers.containsString(".csv")));
    }

    // --- File versioning ---

    @Test
    void fileUpload_sameNameIncrementsVersion() throws Exception {
        MockMultipartFile file1 = new MockMultipartFile(
                "file", "versioned.pdf", "application/pdf", "v1".getBytes());
        String resp1 = mockMvc.perform(multipart("/api/files/upload")
                        .file(file1)
                        .param("module", "PROPERTY")
                        .param("referenceId", "1")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version", is(1)))
                .andReturn().getResponse().getContentAsString();

        MockMultipartFile file2 = new MockMultipartFile(
                "file", "versioned.pdf", "application/pdf", "v2".getBytes());
        mockMvc.perform(multipart("/api/files/upload")
                        .file(file2)
                        .param("module", "PROPERTY")
                        .param("referenceId", "1")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version", is(2)));
    }

    // --- Property compliance ---

    @Test
    void propertyCreate_withComplianceFields_succeeds() throws Exception {
        String body = "{\"name\":\"Compliant Room\",\"type\":\"ROOM\",\"address\":\"Addr\"," +
                "\"capacity\":3,\"complianceStatus\":\"COMPLIANT\"," +
                "\"complianceNotes\":\"Passed inspection\",\"rentalPricePerSlot\":100.00}";

        mockMvc.perform(post("/api/properties")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.complianceStatus", is("COMPLIANT")))
                .andExpect(jsonPath("$.rentalPricePerSlot", is(100.00)));
    }

    // --- SERVICE_STAFF role ---

    @Test
    void serviceStaff_canCompleteAssignedAppointment() throws Exception {
        com.anju.appointment.auth.entity.User staffUser = createUser(
                "staff1", "StaffPass1", "Staff Member",
                com.anju.appointment.auth.entity.Role.SERVICE_STAFF);
        String staffToken = generateToken(staffUser);

        AppointmentSlot slot = createFutureSlot();
        Appointment appointment = createAppointmentWithSlot(slot, dispatcherUser.getId());
        // Assign to staff member
        appointment.setAssignedServiceStaffId(staffUser.getId());
        appointment = appointmentRepository.save(appointment);

        // Staff completes the assigned appointment
        mockMvc.perform(put("/api/appointments/" + appointment.getId() + "/complete")
                        .header("Authorization", "Bearer " + staffToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"completionNotes\":\"Service delivered\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("COMPLETED")));
    }

    @Test
    void serviceStaff_cannotCompleteUnassignedAppointment() throws Exception {
        com.anju.appointment.auth.entity.User staffUser = createUser(
                "staff2", "StaffPass2", "Staff Member 2",
                com.anju.appointment.auth.entity.Role.SERVICE_STAFF);
        String staffToken = generateToken(staffUser);

        AppointmentSlot slot = createFutureSlot();
        Appointment appointment = createAppointmentWithSlot(slot, dispatcherUser.getId());
        // Not assigned to this staff member

        mockMvc.perform(put("/api/appointments/" + appointment.getId() + "/complete")
                        .header("Authorization", "Bearer " + staffToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"completionNotes\":\"Service delivered\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("not assigned")));
    }

    // --- Helpers ---

    private AppointmentSlot createFutureSlot() {
        AppointmentSlot slot = new AppointmentSlot();
        slot.setPropertyId(property.getId());
        slot.setDate(LocalDate.now().plusDays(5));
        slot.setStartTime(LocalTime.of(10, 0));
        slot.setEndTime(LocalTime.of(10, 30));
        slot.setDuration(30);
        slot.setCapacity(3);
        return slotRepository.save(slot);
    }

    private Appointment createAppointment(Long userId) {
        AppointmentSlot slot = createFutureSlot();
        return createAppointmentWithSlot(slot, userId);
    }

    private Appointment createAppointmentWithSlot(AppointmentSlot slot, Long userId) {
        slot.setBookedCount(slot.getBookedCount() + 1);
        slotRepository.save(slot);

        Appointment appointment = new Appointment();
        appointment.setSlotId(slot.getId());
        appointment.setPropertyId(property.getId());
        appointment.setUserId(userId);
        appointment.setPatientName("Test Patient");
        appointment.setPatientPhone("13800138000");
        appointment.setServiceType("GENERAL_CONSULTATION");
        appointment.setStatus(AppointmentStatus.CONFIRMED);
        appointment.setExpiresAt(LocalDateTime.now().plusMinutes(15));
        appointment.setIdempotencyKey(UUID.randomUUID().toString());
        return appointmentRepository.save(appointment);
    }

    // --- Reviewer can list all appointments ---

    @Test
    void reviewer_canListAllAppointments() throws Exception {
        // Create an appointment owned by dispatcher
        createAppointment(dispatcherUser.getId());

        // Reviewer should see all appointments
        mockMvc.perform(get("/api/appointments")
                        .header("Authorization", "Bearer " + reviewerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(
                        org.hamcrest.Matchers.greaterThanOrEqualTo(1)));
    }

    // --- Cross-user idempotency key rejection ---

    @Test
    void bookAppointment_sameIdempotencyKeyDifferentUser_returns409() throws Exception {
        AppointmentSlot slot = createFutureSlot();
        String sharedKey = UUID.randomUUID().toString();
        String body = bookingJson(slot.getId(), sharedKey);

        // First user books
        mockMvc.perform(post("/api/appointments")
                        .header("Authorization", "Bearer " + dispatcherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        // Different user tries same key
        mockMvc.perform(post("/api/appointments")
                        .header("Authorization", "Bearer " + reviewerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("Idempotency key already used")));
    }

    // --- Booking rejected for inactive property ---

    @Test
    void bookAppointment_inactiveProperty_returns409() throws Exception {
        property.setStatus(com.anju.appointment.property.entity.PropertyStatus.INACTIVE);
        property = propertyRepository.save(property);

        AppointmentSlot slot = createFutureSlot();
        String body = bookingJson(slot.getId(), UUID.randomUUID().toString());

        mockMvc.perform(post("/api/appointments")
                        .header("Authorization", "Bearer " + dispatcherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("not active")));
    }

    // --- Booking rejected for non-compliant property ---

    @Test
    void bookAppointment_pendingReviewProperty_returns409() throws Exception {
        property.setComplianceStatus(com.anju.appointment.property.entity.ComplianceStatus.PENDING_REVIEW);
        property = propertyRepository.save(property);

        AppointmentSlot slot = createFutureSlot();
        String body = bookingJson(slot.getId(), UUID.randomUUID().toString());

        mockMvc.perform(post("/api/appointments")
                        .header("Authorization", "Bearer " + dispatcherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("PENDING_REVIEW")));
    }

    // --- Helpers ---

    private String bookingJson(Long slotId, String idempotencyKey) {
        return String.format(
                "{\"slotId\":%d,\"patientName\":\"Li Wei\"," +
                "\"patientPhone\":\"13800138000\",\"serviceType\":\"GENERAL_CONSULTATION\"," +
                "\"notes\":\"Test\",\"idempotencyKey\":\"%s\"}",
                slotId, idempotencyKey);
    }
}
