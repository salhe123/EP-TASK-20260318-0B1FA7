package com.anju.appointment;

import com.anju.appointment.appointment.entity.Appointment;
import com.anju.appointment.appointment.entity.AppointmentSlot;
import com.anju.appointment.appointment.entity.AppointmentStatus;
import com.anju.appointment.appointment.repository.AppointmentRepository;
import com.anju.appointment.appointment.repository.AppointmentSlotRepository;
import com.anju.appointment.audit.repository.AuditLogRepository;
import com.anju.appointment.auth.entity.Role;
import com.anju.appointment.auth.entity.User;
import com.anju.appointment.file.repository.FileRecordRepository;
import com.anju.appointment.financial.entity.Settlement;
import com.anju.appointment.financial.entity.SettlementStatus;
import com.anju.appointment.financial.entity.Transaction;
import com.anju.appointment.financial.entity.TransactionType;
import com.anju.appointment.financial.repository.RefundRepository;
import com.anju.appointment.financial.repository.SettlementRepository;
import com.anju.appointment.financial.repository.TransactionRepository;
import com.anju.appointment.property.entity.ComplianceStatus;
import com.anju.appointment.property.entity.Property;
import com.anju.appointment.property.entity.PropertyStatus;
import com.anju.appointment.property.repository.PropertyRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SecurityHardeningTest extends BaseIntegrationTest {

    @Autowired private PropertyRepository propertyRepository;
    @Autowired private AppointmentSlotRepository slotRepository;
    @Autowired private AppointmentRepository appointmentRepository;
    @Autowired private TransactionRepository transactionRepository;
    @Autowired private RefundRepository refundRepository;
    @Autowired private SettlementRepository settlementRepository;
    @Autowired private FileRecordRepository fileRecordRepository;
    @Autowired private AuditLogRepository auditLogRepository;

    @Value("${app.storage.path}") private String storagePath;

    private Property property;

    @BeforeEach
    void setUp() {
        auditLogRepository.deleteAll();
        settlementRepository.deleteAll();
        refundRepository.deleteAll();
        transactionRepository.deleteAll();
        fileRecordRepository.deleteAll();
        appointmentRepository.deleteAll();
        slotRepository.deleteAll();
        propertyRepository.deleteAll();

        property = new Property();
        property.setName("Hardening Test Room");
        property.setType("ROOM");
        property.setCapacity(3);
        property.setStatus(PropertyStatus.ACTIVE);
        property.setComplianceStatus(ComplianceStatus.COMPLIANT);
        property = propertyRepository.save(property);
    }

    @AfterEach
    void cleanUp() throws IOException {
        Path storage = Paths.get(storagePath).toAbsolutePath().normalize();
        if (Files.exists(storage)) {
            Files.walkFileTree(storage, new SimpleFileVisitor<>() {
                @Override public FileVisitResult visitFile(Path f, BasicFileAttributes a) throws IOException { Files.delete(f); return FileVisitResult.CONTINUE; }
                @Override public FileVisitResult postVisitDirectory(Path d, IOException e) throws IOException { Files.delete(d); return FileVisitResult.CONTINUE; }
            });
        }
    }

    // =========================================================================
    // GAP 1: Security config — cookies, CORS, refresh token body removal
    // =========================================================================

    @Nested
    class CookieAndCorsSecurityTests {

        @Test
        void login_bothCookiesHaveCorrectSecurityFlags() throws Exception {
            var result = mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\":\"admin\",\"password\":\"Admin123\"}"))
                    .andExpect(status().isOk())
                    .andReturn();

            boolean foundAccess = false;
            boolean foundRefresh = false;
            for (String h : result.getResponse().getHeaders("Set-Cookie")) {
                if (h.startsWith("accessToken=")) {
                    foundAccess = true;
                    org.junit.jupiter.api.Assertions.assertTrue(h.contains("HttpOnly"),
                            "accessToken must be HttpOnly, got: " + h);
                    org.junit.jupiter.api.Assertions.assertTrue(h.contains("SameSite=Lax"),
                            "accessToken must have SameSite=Lax in test (secure-cookies=false), got: " + h);
                    org.junit.jupiter.api.Assertions.assertTrue(h.contains("Path=/"),
                            "accessToken must have Path=/, got: " + h);
                }
                if (h.startsWith("refreshToken=")) {
                    foundRefresh = true;
                    org.junit.jupiter.api.Assertions.assertTrue(h.contains("HttpOnly"),
                            "refreshToken must be HttpOnly, got: " + h);
                    org.junit.jupiter.api.Assertions.assertTrue(h.contains("SameSite=Lax"),
                            "refreshToken must have SameSite=Lax in test, got: " + h);
                    org.junit.jupiter.api.Assertions.assertTrue(h.contains("Path=/api/auth"),
                            "refreshToken must have Path=/api/auth, got: " + h);
                    // secure-cookies=false in test, so Secure flag should NOT be present
                    org.junit.jupiter.api.Assertions.assertFalse(h.contains("Secure"),
                            "refreshToken must NOT have Secure in test profile, got: " + h);
                }
            }
            org.junit.jupiter.api.Assertions.assertTrue(foundAccess, "accessToken Set-Cookie must be present");
            org.junit.jupiter.api.Assertions.assertTrue(foundRefresh, "refreshToken Set-Cookie must be present");
        }

        @Test
        void login_refreshTokenNotInJsonBody() throws Exception {
            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\":\"admin\",\"password\":\"Admin123\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accessToken", notNullValue()))
                    .andExpect(jsonPath("$.refreshToken").doesNotExist());
        }

        @Test
        void refresh_refreshTokenNotInJsonBody() throws Exception {
            refreshTokenRepository.deleteAll();

            var loginResult = mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\":\"admin\",\"password\":\"Admin123\"}"))
                    .andExpect(status().isOk())
                    .andReturn();

            String refreshTokenValue = null;
            for (String h : loginResult.getResponse().getHeaders("Set-Cookie")) {
                if (h.startsWith("refreshToken=")) {
                    refreshTokenValue = h.split(";")[0].substring("refreshToken=".length());
                }
            }
            org.junit.jupiter.api.Assertions.assertNotNull(refreshTokenValue);

            mockMvc.perform(post("/api/auth/refresh")
                            .cookie(new jakarta.servlet.http.Cookie("refreshToken", refreshTokenValue)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accessToken", notNullValue()))
                    .andExpect(jsonPath("$.refreshToken").doesNotExist());
        }

        @Test
        void cors_allowedOrigin_includesAccessControlHeader() throws Exception {
            mockMvc.perform(get("/api/properties")
                            .header("Authorization", "Bearer " + adminToken)
                            .header("Origin", "http://localhost:8080"))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:8080"));
        }

        @Test
        void cors_disallowedOrigin_noAccessControlAllowOriginHeader() throws Exception {
            // Disallowed origin: response may still be 200 but must NOT include ACAO header
            var result = mockMvc.perform(get("/api/properties")
                            .header("Authorization", "Bearer " + adminToken)
                            .header("Origin", "http://evil-site.com"))
                    .andReturn();

            String acao = result.getResponse().getHeader("Access-Control-Allow-Origin");
            org.junit.jupiter.api.Assertions.assertNull(acao,
                    "Disallowed origin must not get Access-Control-Allow-Origin header, got: " + acao);
        }
    }

    // =========================================================================
    // GAP 2: DTO leakage regression — sensitive fields must be absent
    // =========================================================================

    @Nested
    class DtoLeakageRegressionTests {

        @Test
        void appointmentResponse_doesNotLeakIdempotencyKeyOrUserId() throws Exception {
            AppointmentSlot slot = createFutureSlot();
            String body = String.format(
                    "{\"slotId\":%d,\"patientName\":\"Li Wei\",\"patientPhone\":\"13800138000\"," +
                    "\"serviceType\":\"GENERAL\",\"idempotencyKey\":\"%s\"}",
                    slot.getId(), UUID.randomUUID());

            mockMvc.perform(post("/api/appointments")
                            .header("Authorization", "Bearer " + dispatcherToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id", notNullValue()))
                    .andExpect(jsonPath("$.idempotencyKey").doesNotExist())
                    .andExpect(jsonPath("$.userId").doesNotExist());
        }

        @Test
        void transactionResponse_doesNotLeakIdempotencyKeyOrCreatedBy() throws Exception {
            Appointment appt = createAppointment();
            String body = String.format(
                    "{\"appointmentId\":%d,\"type\":\"SERVICE_FEE\",\"amount\":100.00," +
                    "\"description\":\"Fee\",\"idempotencyKey\":\"%s\"}",
                    appt.getId(), UUID.randomUUID());

            mockMvc.perform(post("/api/financial/transactions")
                            .header("Authorization", "Bearer " + financeToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id", notNullValue()))
                    .andExpect(jsonPath("$.idempotencyKey").doesNotExist())
                    .andExpect(jsonPath("$.createdBy").doesNotExist());
        }

        @Test
        void refundResponse_doesNotLeakIdempotencyKeyOrCreatedBy() throws Exception {
            Transaction txn = createTransaction();
            String body = String.format(
                    "{\"originalTransactionId\":%d,\"amount\":50.00,\"reason\":\"Test\"," +
                    "\"idempotencyKey\":\"%s\"}",
                    txn.getId(), UUID.randomUUID());

            mockMvc.perform(post("/api/financial/refunds")
                            .header("Authorization", "Bearer " + financeToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.idempotencyKey").doesNotExist())
                    .andExpect(jsonPath("$.createdBy").doesNotExist());
        }

        @Test
        void fileResponse_doesNotLeakFilePathOrUploadedBy() throws Exception {
            MockMultipartFile file = new MockMultipartFile(
                    "file", "test.pdf", "application/pdf", "content".getBytes());

            mockMvc.perform(multipart("/api/files/upload")
                            .file(file)
                            .param("module", "PROPERTY")
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id", notNullValue()))
                    .andExpect(jsonPath("$.filePath").doesNotExist())
                    .andExpect(jsonPath("$.uploadedBy").doesNotExist());
        }

        @Test
        void settlementResponse_doesNotLeakCreatedBy() throws Exception {
            String today = LocalDate.now().toString();
            String body = String.format(
                    "{\"periodStart\":\"%s\",\"periodEnd\":\"%s\",\"notes\":\"Test\"}", today, today);

            mockMvc.perform(post("/api/financial/settlements")
                            .header("Authorization", "Bearer " + financeToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.createdBy").doesNotExist());
        }
    }

    // =========================================================================
    // GAP 3: Endpoint coverage — logout, admin mgmt, settlement, audit, slots
    // =========================================================================

    @Nested
    class EndpointCoverageTests {

        @Test
        void logout_returnsOkAndClearsBothCookies() throws Exception {
            var result = mockMvc.perform(post("/api/auth/logout")
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isOk())
                    .andReturn();

            boolean clearedAccess = false;
            boolean clearedRefresh = false;
            for (String h : result.getResponse().getHeaders("Set-Cookie")) {
                if (h.startsWith("accessToken=;") && h.contains("Max-Age=0")) {
                    clearedAccess = true;
                }
                if (h.startsWith("refreshToken=;") && h.contains("Max-Age=0")) {
                    clearedRefresh = true;
                }
            }
            org.junit.jupiter.api.Assertions.assertTrue(clearedAccess,
                    "Logout must clear accessToken cookie with Max-Age=0");
            org.junit.jupiter.api.Assertions.assertTrue(clearedRefresh,
                    "Logout must clear refreshToken cookie with Max-Age=0");
        }

        @Test
        void adminGetUser_returnsUserDetails() throws Exception {
            mockMvc.perform(get("/api/admin/users/" + dispatcherUser.getId())
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.username", is("dispatcher")));
        }

        @Test
        void adminDisableUser_succeeds() throws Exception {
            User target = createUser("target1", "Pass1234!xxx", "Target", Role.FINANCE);

            mockMvc.perform(put("/api/admin/users/" + target.getId() + "/disable")
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.enabled", is(false)));
        }

        @Test
        void adminEnableUser_succeeds() throws Exception {
            User target = createUser("target2", "Pass1234!xxx", "Target", Role.FINANCE);
            target.setEnabled(false);
            userRepository.save(target);

            mockMvc.perform(put("/api/admin/users/" + target.getId() + "/enable")
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.enabled", is(true)));
        }

        @Test
        void adminUnlockUser_succeeds() throws Exception {
            User target = createUser("target3", "Pass1234!xxx", "Target", Role.FINANCE);
            target.setLocked(true);
            target.setLockExpiresAt(LocalDateTime.now().plusHours(1));
            userRepository.save(target);

            mockMvc.perform(put("/api/admin/users/" + target.getId() + "/unlock")
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isOk());
        }

        @Test
        void adminUpdateUser_succeeds() throws Exception {
            User target = createUser("target4", "Pass1234!xxx", "Target", Role.FINANCE);

            mockMvc.perform(put("/api/admin/users/" + target.getId())
                            .header("Authorization", "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"fullName\":\"Updated Name\",\"phone\":\"13900000000\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.fullName", is("Updated Name")));
        }

        @Test
        void adminResetPassword_succeeds() throws Exception {
            User target = createUser("resetme", "Pass1234!xxx", "Reset Target", Role.FINANCE);

            // secondary-verification=false in test, so verificationPassword not required
            mockMvc.perform(put("/api/admin/users/" + target.getId() + "/reset-password")
                            .header("Authorization", "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"newPassword\":\"NewReset1!xxx\"}"))
                    .andExpect(status().isOk());
        }

        @Test
        void adminResetPassword_nonAdminDenied() throws Exception {
            User target = createUser("resetme2", "Pass1234!xxx", "Reset Target", Role.FINANCE);

            mockMvc.perform(put("/api/admin/users/" + target.getId() + "/reset-password")
                            .header("Authorization", "Bearer " + financeToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"newPassword\":\"NewReset1!xxx\"}"))
                    .andExpect(status().isForbidden());
        }

        @Test
        void settlementRead_succeeds() throws Exception {
            Settlement s = new Settlement();
            s.setPeriodStart(LocalDate.now());
            s.setPeriodEnd(LocalDate.now());
            s.setTotalTransactions(BigDecimal.valueOf(500));
            s.setTotalRefunds(BigDecimal.ZERO);
            s.setNetAmount(BigDecimal.valueOf(500));
            s.setTransactionCount(2);
            s.setRefundCount(0);
            s.setCurrency("CNY");
            s.setStatus(SettlementStatus.DRAFT);
            s.setCreatedBy(financeUser.getId());
            s = settlementRepository.save(s);

            mockMvc.perform(get("/api/financial/settlements/" + s.getId())
                            .header("Authorization", "Bearer " + financeToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status", is("DRAFT")));
        }

        @Test
        void settlementList_succeeds() throws Exception {
            mockMvc.perform(get("/api/financial/settlements")
                            .header("Authorization", "Bearer " + financeToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").isNumber());
        }

        @Test
        void settlementConfirm_succeeds() throws Exception {
            Settlement s = new Settlement();
            s.setPeriodStart(LocalDate.now());
            s.setPeriodEnd(LocalDate.now());
            s.setTotalTransactions(BigDecimal.valueOf(100));
            s.setTotalRefunds(BigDecimal.ZERO);
            s.setNetAmount(BigDecimal.valueOf(100));
            s.setTransactionCount(1);
            s.setRefundCount(0);
            s.setCurrency("CNY");
            s.setStatus(SettlementStatus.DRAFT);
            s.setCreatedBy(financeUser.getId());
            s = settlementRepository.save(s);

            mockMvc.perform(put("/api/financial/settlements/" + s.getId() + "/confirm")
                            .header("Authorization", "Bearer " + financeToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status", is("CONFIRMED")));
        }

        @Test
        void auditLogs_adminCanAccess() throws Exception {
            mockMvc.perform(get("/api/audit/logs")
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").isNumber());
        }

        @Test
        void auditLogs_nonAdminDenied() throws Exception {
            mockMvc.perform(get("/api/audit/logs")
                            .header("Authorization", "Bearer " + financeToken))
                    .andExpect(status().isForbidden());
        }

        @Test
        void auditLogs_filterByModule() throws Exception {
            // Create a property to generate an audit log
            mockMvc.perform(post("/api/properties")
                    .header("Authorization", "Bearer " + adminToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\":\"Audit Room\",\"type\":\"ROOM\",\"capacity\":2," +
                            "\"complianceStatus\":\"COMPLIANT\"}"));

            mockMvc.perform(get("/api/audit/logs")
                            .header("Authorization", "Bearer " + adminToken)
                            .param("module", "PROPERTY"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements", greaterThanOrEqualTo(1)));
        }

        @Test
        void slotListing_returnsSlots() throws Exception {
            AppointmentSlot slot = createFutureSlot();

            mockMvc.perform(get("/api/appointments/slots")
                            .header("Authorization", "Bearer " + dispatcherToken)
                            .param("propertyId", property.getId().toString())
                            .param("date", slot.getDate().toString()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))))
                    .andExpect(jsonPath("$[0].startTime", notNullValue()));
        }
    }

    // =========================================================================
    // GAP 4: Bugfix regression — dates, version history, audit attribution
    // =========================================================================

    @Nested
    class BugfixRegressionTests {

        @Test
        void appointmentList_invalidDate_returns409NotServerError() throws Exception {
            mockMvc.perform(get("/api/appointments")
                            .header("Authorization", "Bearer " + adminToken)
                            .param("dateFrom", "not-a-date"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message", containsString("Invalid date")));
        }

        @Test
        void transactionList_invalidDate_returns409() throws Exception {
            mockMvc.perform(get("/api/financial/transactions")
                            .header("Authorization", "Bearer " + financeToken)
                            .param("dateFrom", "xyz"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message", containsString("Invalid date")));
        }

        @Test
        void auditLogs_invalidDate_returns409() throws Exception {
            mockMvc.perform(get("/api/audit/logs")
                            .header("Authorization", "Bearer " + adminToken)
                            .param("dateFrom", "2026-99-99"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message", containsString("Invalid date")));
        }

        @Test
        void fileVersionHistory_fromNonRootVersion_returnsFullChain() throws Exception {
            MockMultipartFile v1 = new MockMultipartFile(
                    "file", "chain.pdf", "application/pdf", "v1".getBytes());
            MockMultipartFile v2 = new MockMultipartFile(
                    "file", "chain.pdf", "application/pdf", "v2".getBytes());

            mockMvc.perform(multipart("/api/files/upload").file(v1)
                    .param("module", "PROPERTY").param("referenceId", "1")
                    .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isOk());

            String v2Resp = mockMvc.perform(multipart("/api/files/upload").file(v2)
                            .param("module", "PROPERTY").param("referenceId", "1")
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.version", is(2)))
                    .andReturn().getResponse().getContentAsString();

            Long v2Id = objectMapper.readTree(v2Resp).get("id").asLong();

            // Query history from the SECOND version — should still return full chain
            mockMvc.perform(get("/api/files/" + v2Id + "/versions")
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(2)))
                    .andExpect(jsonPath("$[0].version", is(2)))
                    .andExpect(jsonPath("$[1].version", is(1)));
        }

        @Test
        void fileUpload_withoutReferenceId_versioningWorks() throws Exception {
            MockMultipartFile v1 = new MockMultipartFile(
                    "file", "norefs.pdf", "application/pdf", "v1".getBytes());
            MockMultipartFile v2 = new MockMultipartFile(
                    "file", "norefs.pdf", "application/pdf", "v2".getBytes());

            mockMvc.perform(multipart("/api/files/upload").file(v1)
                    .param("module", "APPOINTMENT")
                    .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.version", is(1)));

            mockMvc.perform(multipart("/api/files/upload").file(v2)
                            .param("module", "APPOINTMENT")
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.version", is(2)));
        }

        @Test
        void auditLog_propertyCreate_hasActorUsername() throws Exception {
            mockMvc.perform(post("/api/properties")
                    .header("Authorization", "Bearer " + adminToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\":\"Audit Actor Room\",\"type\":\"ROOM\",\"capacity\":2," +
                            "\"complianceStatus\":\"COMPLIANT\"}"))
                    .andExpect(status().isOk());

            // Check audit log has username populated
            mockMvc.perform(get("/api/audit/logs")
                            .header("Authorization", "Bearer " + adminToken)
                            .param("module", "PROPERTY")
                            .param("operation", "CREATE"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].username", is("admin")));
        }

        @Test
        void auditLog_appointmentCreate_hasActorUsername() throws Exception {
            AppointmentSlot slot = createFutureSlot();
            String body = String.format(
                    "{\"slotId\":%d,\"patientName\":\"Li Wei\",\"patientPhone\":\"13800138000\"," +
                    "\"serviceType\":\"GENERAL\",\"idempotencyKey\":\"%s\"}",
                    slot.getId(), UUID.randomUUID());

            mockMvc.perform(post("/api/appointments")
                    .header("Authorization", "Bearer " + dispatcherToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
                    .andExpect(status().isOk());

            mockMvc.perform(get("/api/audit/logs")
                            .header("Authorization", "Bearer " + adminToken)
                            .param("module", "APPOINTMENT")
                            .param("operation", "CREATE"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].username", is("dispatcher")));
        }
    }

    // =========================================================================
    // Helpers
    // =========================================================================

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

    private Appointment createAppointment() {
        AppointmentSlot slot = createFutureSlot();
        slot.setBookedCount(1);
        slotRepository.save(slot);

        Appointment a = new Appointment();
        a.setSlotId(slot.getId());
        a.setPropertyId(property.getId());
        a.setUserId(financeUser.getId());
        a.setPatientName("Test Patient");
        a.setPatientPhone("13800138000");
        a.setServiceType("GENERAL");
        a.setStatus(AppointmentStatus.CONFIRMED);
        a.setExpiresAt(LocalDateTime.now().plusMinutes(15));
        a.setIdempotencyKey(UUID.randomUUID().toString());
        return appointmentRepository.save(a);
    }

    private Transaction createTransaction() {
        Appointment appt = createAppointment();
        Transaction t = new Transaction();
        t.setAppointmentId(appt.getId());
        t.setType(TransactionType.SERVICE_FEE);
        t.setAmount(BigDecimal.valueOf(500));
        t.setCurrency("CNY");
        t.setDescription("Test");
        t.setIdempotencyKey(UUID.randomUUID().toString());
        t.setCreatedBy(financeUser.getId());
        return transactionRepository.save(t);
    }
}
