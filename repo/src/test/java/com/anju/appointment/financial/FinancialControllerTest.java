package com.anju.appointment.financial;

import com.anju.appointment.BaseIntegrationTest;
import com.anju.appointment.appointment.entity.Appointment;
import com.anju.appointment.appointment.entity.AppointmentSlot;
import com.anju.appointment.appointment.entity.AppointmentStatus;
import com.anju.appointment.appointment.repository.AppointmentRepository;
import com.anju.appointment.appointment.repository.AppointmentSlotRepository;
import com.anju.appointment.financial.entity.Refund;
import com.anju.appointment.financial.entity.Transaction;
import com.anju.appointment.financial.entity.TransactionType;
import com.anju.appointment.financial.repository.RefundRepository;
import com.anju.appointment.financial.repository.TransactionRepository;
import com.anju.appointment.property.entity.Property;
import com.anju.appointment.property.entity.PropertyStatus;
import com.anju.appointment.property.repository.PropertyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;

import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class FinancialControllerTest extends BaseIntegrationTest {

    @Autowired
    private PropertyRepository propertyRepository;
    @Autowired
    private AppointmentSlotRepository slotRepository;
    @Autowired
    private AppointmentRepository appointmentRepository;
    @Autowired
    private TransactionRepository transactionRepository;
    @Autowired
    private RefundRepository refundRepository;

    private Appointment appointment;

    @BeforeEach
    void setUp() {
        refundRepository.deleteAll();
        transactionRepository.deleteAll();
        appointmentRepository.deleteAll();
        slotRepository.deleteAll();
        propertyRepository.deleteAll();

        Property property = new Property();
        property.setName("Test Room");
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

        appointment = new Appointment();
        appointment.setSlotId(slot.getId());
        appointment.setPropertyId(property.getId());
        appointment.setUserId(financeUser.getId());
        appointment.setPatientName("Test Patient");
        appointment.setPatientPhone("13800138000");
        appointment.setServiceType("GENERAL");
        appointment.setStatus(AppointmentStatus.CONFIRMED);
        appointment.setExpiresAt(LocalDateTime.now().plusMinutes(15));
        appointment.setIdempotencyKey(UUID.randomUUID().toString());
        appointment = appointmentRepository.save(appointment);
    }

    @Test
    void createTransaction_withIdempotencyKey_succeeds() throws Exception {
        String key = UUID.randomUUID().toString();
        String body = String.format(
                "{\"appointmentId\":%d,\"type\":\"SERVICE_FEE\",\"amount\":500.00," +
                "\"currency\":\"CNY\",\"description\":\"Consultation fee\",\"idempotencyKey\":\"%s\"}",
                appointment.getId(), key);

        mockMvc.perform(post("/api/financial/transactions")
                        .header("Authorization", "Bearer " + financeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type", is("SERVICE_FEE")))
                .andExpect(jsonPath("$.amount", is(500.00)))
                .andExpect(jsonPath("$.status", is("RECORDED")));
    }

    @Test
    void createTransaction_duplicateKey_returnsExisting() throws Exception {
        String key = UUID.randomUUID().toString();
        String body = String.format(
                "{\"appointmentId\":%d,\"type\":\"SERVICE_FEE\",\"amount\":500.00," +
                "\"description\":\"Fee\",\"idempotencyKey\":\"%s\"}",
                appointment.getId(), key);

        mockMvc.perform(post("/api/financial/transactions")
                .header("Authorization", "Bearer " + financeToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));

        // Second request with same key
        mockMvc.perform(post("/api/financial/transactions")
                        .header("Authorization", "Bearer " + financeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNumber());

        assertEquals(1, transactionRepository.count());
    }

    @Test
    void createRefund_success() throws Exception {
        Transaction transaction = createTransaction(BigDecimal.valueOf(500));

        String body = String.format(
                "{\"originalTransactionId\":%d,\"amount\":250.00," +
                "\"reason\":\"Partial refund\",\"idempotencyKey\":\"%s\"}",
                transaction.getId(), UUID.randomUUID().toString());

        mockMvc.perform(post("/api/financial/refunds")
                        .header("Authorization", "Bearer " + financeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount", is(250.00)))
                .andExpect(jsonPath("$.status", is("RECORDED")));
    }

    @Test
    void createRefund_exceedsOriginalAmount_returns409() throws Exception {
        Transaction transaction = createTransaction(BigDecimal.valueOf(500));

        // First refund: 300
        Refund firstRefund = new Refund();
        firstRefund.setOriginalTransactionId(transaction.getId());
        firstRefund.setAmount(BigDecimal.valueOf(300));
        firstRefund.setReason("First partial");
        firstRefund.setIdempotencyKey(UUID.randomUUID().toString());
        firstRefund.setCreatedBy(financeUser.getId());
        refundRepository.save(firstRefund);

        // Second refund: 250 (300 + 250 = 550 > 500)
        String body = String.format(
                "{\"originalTransactionId\":%d,\"amount\":250.00," +
                "\"reason\":\"Second partial\",\"idempotencyKey\":\"%s\"}",
                transaction.getId(), UUID.randomUUID().toString());

        mockMvc.perform(post("/api/financial/refunds")
                        .header("Authorization", "Bearer " + financeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("exceeds original")));
    }

    @Test
    void dailyReport_aggregatesCorrectly() throws Exception {
        createTransaction(BigDecimal.valueOf(500));
        createTransaction(BigDecimal.valueOf(300));

        // Create a refund for today
        Transaction transactionForRefund = createTransaction(BigDecimal.valueOf(200));
        Refund refund = new Refund();
        refund.setOriginalTransactionId(transactionForRefund.getId());
        refund.setAmount(BigDecimal.valueOf(100));
        refund.setReason("Partial refund");
        refund.setIdempotencyKey(UUID.randomUUID().toString());
        refund.setCreatedBy(financeUser.getId());
        refundRepository.save(refund);

        String today = LocalDate.now().toString();
        mockMvc.perform(get("/api/financial/reports/daily")
                        .header("Authorization", "Bearer " + financeToken)
                        .param("date", today))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalTransactions", is(3)))
                .andExpect(jsonPath("$.totalAmount", is(1000.00)))
                .andExpect(jsonPath("$.totalRefunds", is(100.00)))
                .andExpect(jsonPath("$.netAmount", is(900.00)))
                .andExpect(jsonPath("$.currency", is("CNY")));
    }

    private Transaction createTransaction(BigDecimal amount) {
        Transaction transaction = new Transaction();
        transaction.setAppointmentId(appointment.getId());
        transaction.setType(TransactionType.SERVICE_FEE);
        transaction.setAmount(amount);
        transaction.setCurrency("CNY");
        transaction.setDescription("Test transaction");
        transaction.setIdempotencyKey(UUID.randomUUID().toString());
        transaction.setCreatedBy(financeUser.getId());
        return transactionRepository.save(transaction);
    }
}
