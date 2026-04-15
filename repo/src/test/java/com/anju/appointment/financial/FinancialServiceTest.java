package com.anju.appointment.financial;

import com.anju.appointment.appointment.repository.AppointmentRepository;
import com.anju.appointment.audit.service.AuditService;
import com.anju.appointment.common.BusinessRuleException;
import com.anju.appointment.common.ResourceNotFoundException;
import com.anju.appointment.financial.dto.DailyReportResponse;
import com.anju.appointment.financial.dto.RefundRequest;
import com.anju.appointment.financial.dto.RefundResponse;
import com.anju.appointment.financial.dto.SettlementRequest;
import com.anju.appointment.financial.dto.SettlementResponse;
import com.anju.appointment.financial.dto.TransactionRequest;
import com.anju.appointment.financial.dto.TransactionResponse;
import com.anju.appointment.financial.entity.Refund;
import com.anju.appointment.financial.entity.Settlement;
import com.anju.appointment.financial.entity.SettlementStatus;
import com.anju.appointment.financial.entity.Transaction;
import com.anju.appointment.financial.entity.TransactionType;
import com.anju.appointment.financial.repository.RefundRepository;
import com.anju.appointment.financial.repository.SettlementRepository;
import com.anju.appointment.financial.repository.TransactionRepository;
import com.anju.appointment.financial.service.FinancialService;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FinancialServiceTest {

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private RefundRepository refundRepository;

    @Mock
    private SettlementRepository settlementRepository;

    @Mock
    private AppointmentRepository appointmentRepository;

    @Mock
    private AuditService auditService;

    @InjectMocks
    private FinancialService financialService;

    @Captor
    private ArgumentCaptor<Transaction> transactionCaptor;

    @Captor
    private ArgumentCaptor<Refund> refundCaptor;

    @Captor
    private ArgumentCaptor<Settlement> settlementCaptor;

    private static final Long USER_ID = 1L;
    private static final Long APPOINTMENT_ID = 100L;

    // ---------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------

    private TransactionRequest buildTransactionRequest(String type, BigDecimal amount, String idempotencyKey) {
        TransactionRequest req = new TransactionRequest();
        req.setAppointmentId(APPOINTMENT_ID);
        req.setType(type);
        req.setAmount(amount);
        req.setCurrency("CNY");
        req.setDescription("Test transaction");
        req.setIdempotencyKey(idempotencyKey);
        return req;
    }

    private Transaction buildTransaction(Long id, TransactionType type, BigDecimal amount, String idempotencyKey) {
        Transaction t = new Transaction();
        t.setId(id);
        t.setAppointmentId(APPOINTMENT_ID);
        t.setType(type);
        t.setAmount(amount);
        t.setCurrency("CNY");
        t.setDescription("Test transaction");
        t.setIdempotencyKey(idempotencyKey);
        t.setCreatedBy(USER_ID);
        t.setCreatedAt(LocalDateTime.now());
        t.setUpdatedAt(LocalDateTime.now());
        return t;
    }

    private RefundRequest buildRefundRequest(Long originalTxId, BigDecimal amount, String idempotencyKey) {
        RefundRequest req = new RefundRequest();
        req.setOriginalTransactionId(originalTxId);
        req.setAmount(amount);
        req.setReason("Customer request");
        req.setIdempotencyKey(idempotencyKey);
        return req;
    }

    private Refund buildRefund(Long id, Long originalTxId, BigDecimal amount, String idempotencyKey) {
        Refund r = new Refund();
        r.setId(id);
        r.setOriginalTransactionId(originalTxId);
        r.setAmount(amount);
        r.setReason("Customer request");
        r.setIdempotencyKey(idempotencyKey);
        r.setCreatedBy(USER_ID);
        r.setCreatedAt(LocalDateTime.now());
        return r;
    }

    private Settlement buildSettlement(Long id, LocalDate start, LocalDate end, SettlementStatus status) {
        Settlement s = new Settlement();
        s.setId(id);
        s.setPeriodStart(start);
        s.setPeriodEnd(end);
        s.setTotalTransactions(new BigDecimal("1000.00"));
        s.setTotalRefunds(new BigDecimal("100.00"));
        s.setNetAmount(new BigDecimal("900.00"));
        s.setTransactionCount(5);
        s.setRefundCount(1);
        s.setCurrency("CNY");
        s.setStatus(status);
        s.setNotes("Test settlement");
        s.setCreatedBy(USER_ID);
        s.setCreatedAt(LocalDateTime.now());
        s.setUpdatedAt(LocalDateTime.now());
        return s;
    }

    // ---------------------------------------------------------------------------
    // createTransaction
    // ---------------------------------------------------------------------------

    @Nested
    class CreateTransaction {

        @Test
        void success() {
            TransactionRequest request = buildTransactionRequest("SERVICE_FEE", new BigDecimal("200.00"), "txn-key-1");
            Transaction saved = buildTransaction(10L, TransactionType.SERVICE_FEE, new BigDecimal("200.00"), "txn-key-1");

            when(transactionRepository.findByIdempotencyKey("txn-key-1")).thenReturn(Optional.empty());
            when(appointmentRepository.existsById(APPOINTMENT_ID)).thenReturn(true);
            when(transactionRepository.save(any(Transaction.class))).thenReturn(saved);

            TransactionResponse response = financialService.createTransaction(request, USER_ID);

            assertThat(response.getId()).isEqualTo(10L);
            assertThat(response.getType()).isEqualTo("SERVICE_FEE");
            assertThat(response.getAmount()).isEqualByComparingTo("200.00");
            assertThat(response.getCurrency()).isEqualTo("CNY");

            verify(transactionRepository).save(transactionCaptor.capture());
            Transaction captured = transactionCaptor.getValue();
            assertThat(captured.getType()).isEqualTo(TransactionType.SERVICE_FEE);
            assertThat(captured.getCreatedBy()).isEqualTo(USER_ID);
        }

        @Test
        void idempotencyReturnsExisting() {
            Transaction existing = buildTransaction(10L, TransactionType.SERVICE_FEE, new BigDecimal("200.00"), "txn-key-1");
            when(transactionRepository.findByIdempotencyKey("txn-key-1")).thenReturn(Optional.of(existing));

            TransactionRequest request = buildTransactionRequest("SERVICE_FEE", new BigDecimal("200.00"), "txn-key-1");
            TransactionResponse response = financialService.createTransaction(request, USER_ID);

            assertThat(response.getId()).isEqualTo(10L);
            verify(transactionRepository, never()).save(any());
            verify(appointmentRepository, never()).existsById(anyLong());
        }

        @Test
        void appointmentNotFound() {
            TransactionRequest request = buildTransactionRequest("SERVICE_FEE", new BigDecimal("200.00"), "txn-key-2");
            when(transactionRepository.findByIdempotencyKey("txn-key-2")).thenReturn(Optional.empty());
            when(appointmentRepository.existsById(APPOINTMENT_ID)).thenReturn(false);

            assertThatThrownBy(() -> financialService.createTransaction(request, USER_ID))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Appointment not found");
        }

        @Test
        void invalidType() {
            TransactionRequest request = buildTransactionRequest("INVALID_TYPE", new BigDecimal("200.00"), "txn-key-3");
            when(transactionRepository.findByIdempotencyKey("txn-key-3")).thenReturn(Optional.empty());
            when(appointmentRepository.existsById(APPOINTMENT_ID)).thenReturn(true);

            assertThatThrownBy(() -> financialService.createTransaction(request, USER_ID))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("Invalid transaction type");
        }

        @Test
        void auditLogged() {
            TransactionRequest request = buildTransactionRequest("DEPOSIT", new BigDecimal("500.00"), "txn-key-4");
            Transaction saved = buildTransaction(11L, TransactionType.DEPOSIT, new BigDecimal("500.00"), "txn-key-4");

            when(transactionRepository.findByIdempotencyKey("txn-key-4")).thenReturn(Optional.empty());
            when(appointmentRepository.existsById(APPOINTMENT_ID)).thenReturn(true);
            when(transactionRepository.save(any(Transaction.class))).thenReturn(saved);

            financialService.createTransaction(request, USER_ID);

            verify(auditService).log(
                    eq(USER_ID), isNull(), eq("FINANCIAL"), eq("CREATE"),
                    eq("Transaction"), eq(11L),
                    anyString(), isNull());
        }
    }

    // ---------------------------------------------------------------------------
    // getTransaction
    // ---------------------------------------------------------------------------

    @Nested
    class GetTransaction {

        @Test
        void found() {
            Transaction tx = buildTransaction(10L, TransactionType.SERVICE_FEE, new BigDecimal("200.00"), "key-1");
            when(transactionRepository.findById(10L)).thenReturn(Optional.of(tx));

            TransactionResponse response = financialService.getTransaction(10L);

            assertThat(response.getId()).isEqualTo(10L);
            assertThat(response.getType()).isEqualTo("SERVICE_FEE");
        }

        @Test
        void notFound() {
            when(transactionRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> financialService.getTransaction(999L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Transaction not found");
        }
    }

    // ---------------------------------------------------------------------------
    // createRefund
    // ---------------------------------------------------------------------------

    @Nested
    class CreateRefund {

        @Test
        void success() {
            Long originalTxId = 10L;
            Transaction originalTx = buildTransaction(originalTxId, TransactionType.SERVICE_FEE, new BigDecimal("500.00"), "tx-key");
            Refund saved = buildRefund(20L, originalTxId, new BigDecimal("100.00"), "refund-key-1");

            RefundRequest request = buildRefundRequest(originalTxId, new BigDecimal("100.00"), "refund-key-1");

            when(refundRepository.findByIdempotencyKey("refund-key-1")).thenReturn(Optional.empty());
            when(transactionRepository.findById(originalTxId)).thenReturn(Optional.of(originalTx));
            when(refundRepository.sumRefundsByTransactionId(originalTxId)).thenReturn(BigDecimal.ZERO);
            when(refundRepository.save(any(Refund.class))).thenReturn(saved);

            RefundResponse response = financialService.createRefund(request, USER_ID);

            assertThat(response.getId()).isEqualTo(20L);
            assertThat(response.getAmount()).isEqualByComparingTo("100.00");
            assertThat(response.getOriginalTransactionId()).isEqualTo(originalTxId);
        }

        @Test
        void idempotencyReturnsExisting() {
            Refund existing = buildRefund(20L, 10L, new BigDecimal("100.00"), "refund-key-1");
            when(refundRepository.findByIdempotencyKey("refund-key-1")).thenReturn(Optional.of(existing));

            RefundRequest request = buildRefundRequest(10L, new BigDecimal("100.00"), "refund-key-1");
            RefundResponse response = financialService.createRefund(request, USER_ID);

            assertThat(response.getId()).isEqualTo(20L);
            verify(transactionRepository, never()).findById(anyLong());
            verify(refundRepository, never()).save(any());
        }

        @Test
        void exceedsOriginalAmountThrows() {
            Long originalTxId = 10L;
            Transaction originalTx = buildTransaction(originalTxId, TransactionType.SERVICE_FEE, new BigDecimal("500.00"), "tx-key");

            RefundRequest request = buildRefundRequest(originalTxId, new BigDecimal("300.00"), "refund-key-2");

            when(refundRepository.findByIdempotencyKey("refund-key-2")).thenReturn(Optional.empty());
            when(transactionRepository.findById(originalTxId)).thenReturn(Optional.of(originalTx));
            when(refundRepository.sumRefundsByTransactionId(originalTxId)).thenReturn(new BigDecimal("300.00"));

            assertThatThrownBy(() -> financialService.createRefund(request, USER_ID))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("Refund amount exceeds original transaction amount");
        }

        @Test
        void originalNotFound() {
            RefundRequest request = buildRefundRequest(999L, new BigDecimal("100.00"), "refund-key-3");
            when(refundRepository.findByIdempotencyKey("refund-key-3")).thenReturn(Optional.empty());
            when(transactionRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> financialService.createRefund(request, USER_ID))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Original transaction not found");
        }

        @Test
        void auditLogged() {
            Long originalTxId = 10L;
            Transaction originalTx = buildTransaction(originalTxId, TransactionType.SERVICE_FEE, new BigDecimal("500.00"), "tx-key");
            Refund saved = buildRefund(21L, originalTxId, new BigDecimal("50.00"), "refund-key-4");

            RefundRequest request = buildRefundRequest(originalTxId, new BigDecimal("50.00"), "refund-key-4");

            when(refundRepository.findByIdempotencyKey("refund-key-4")).thenReturn(Optional.empty());
            when(transactionRepository.findById(originalTxId)).thenReturn(Optional.of(originalTx));
            when(refundRepository.sumRefundsByTransactionId(originalTxId)).thenReturn(BigDecimal.ZERO);
            when(refundRepository.save(any(Refund.class))).thenReturn(saved);

            financialService.createRefund(request, USER_ID);

            verify(auditService).log(
                    eq(USER_ID), isNull(), eq("FINANCIAL"), eq("CREATE"),
                    eq("Refund"), eq(21L),
                    anyString(), isNull());
        }
    }

    // ---------------------------------------------------------------------------
    // getDailyReport
    // ---------------------------------------------------------------------------

    @Nested
    class GetDailyReport {

        @Test
        void aggregatesCorrectlyWithMultipleTransactionTypes() {
            LocalDate date = LocalDate.of(2026, 4, 10);
            LocalDateTime dayStart = date.atStartOfDay();
            LocalDateTime dayEnd = date.plusDays(1).atStartOfDay();

            Transaction t1 = buildTransaction(1L, TransactionType.SERVICE_FEE, new BigDecimal("200.00"), "k1");
            Transaction t2 = buildTransaction(2L, TransactionType.SERVICE_FEE, new BigDecimal("300.00"), "k2");
            Transaction t3 = buildTransaction(3L, TransactionType.DEPOSIT, new BigDecimal("150.00"), "k3");
            Transaction t4 = buildTransaction(4L, TransactionType.PENALTY, new BigDecimal("50.00"), "k4");

            when(transactionRepository.findByDay(dayStart, dayEnd)).thenReturn(List.of(t1, t2, t3, t4));
            when(refundRepository.sumRefundsByDay(dayStart, dayEnd)).thenReturn(new BigDecimal("80.00"));

            DailyReportResponse report = financialService.getDailyReport(date);

            assertThat(report.getDate()).isEqualTo(date);
            assertThat(report.getTotalTransactions()).isEqualTo(4);
            assertThat(report.getTotalAmount()).isEqualByComparingTo("700.00");
            assertThat(report.getTotalRefunds()).isEqualByComparingTo("80.00");
            assertThat(report.getNetAmount()).isEqualByComparingTo("620.00");
            assertThat(report.getCurrency()).isEqualTo("CNY");

            assertThat(report.getByType()).hasSize(3);

            DailyReportResponse.TypeSummary serviceFee = report.getByType().stream()
                    .filter(ts -> "SERVICE_FEE".equals(ts.getType()))
                    .findFirst().orElseThrow();
            assertThat(serviceFee.getCount()).isEqualTo(2);
            assertThat(serviceFee.getAmount()).isEqualByComparingTo("500.00");

            DailyReportResponse.TypeSummary deposit = report.getByType().stream()
                    .filter(ts -> "DEPOSIT".equals(ts.getType()))
                    .findFirst().orElseThrow();
            assertThat(deposit.getCount()).isEqualTo(1);
            assertThat(deposit.getAmount()).isEqualByComparingTo("150.00");

            DailyReportResponse.TypeSummary penalty = report.getByType().stream()
                    .filter(ts -> "PENALTY".equals(ts.getType()))
                    .findFirst().orElseThrow();
            assertThat(penalty.getCount()).isEqualTo(1);
            assertThat(penalty.getAmount()).isEqualByComparingTo("50.00");
        }
    }

    // ---------------------------------------------------------------------------
    // exportDailyReportCsv
    // ---------------------------------------------------------------------------

    @Nested
    class ExportDailyReportCsv {

        @Test
        void producesValidCsv() {
            LocalDate date = LocalDate.of(2026, 4, 10);
            LocalDateTime dayStart = date.atStartOfDay();
            LocalDateTime dayEnd = date.plusDays(1).atStartOfDay();

            Transaction t1 = buildTransaction(1L, TransactionType.SERVICE_FEE, new BigDecimal("200.00"), "k1");
            Transaction t2 = buildTransaction(2L, TransactionType.DEPOSIT, new BigDecimal("100.00"), "k2");

            when(transactionRepository.findByDay(dayStart, dayEnd)).thenReturn(List.of(t1, t2));
            when(refundRepository.sumRefundsByDay(dayStart, dayEnd)).thenReturn(new BigDecimal("50.00"));

            String csv = financialService.exportDailyReportCsv(date);

            // Verify header line
            String[] lines = csv.split("\n");
            assertThat(lines[0]).isEqualTo("Date,Total Transactions,Total Amount,Total Refunds,Net Amount,Currency");

            // Verify summary line
            assertThat(lines[1]).startsWith("2026-04-10,2,");
            assertThat(lines[1]).contains("300.00");
            assertThat(lines[1]).contains("50.00");
            assertThat(lines[1]).contains("250.00");
            assertThat(lines[1]).endsWith(",CNY");

            // Verify type breakdown header
            assertThat(lines[3]).isEqualTo("Type,Count,Amount");

            // Verify type breakdown rows exist (at least 2)
            assertThat(lines.length).isGreaterThanOrEqualTo(5);

            // Collect all type lines
            String typeBlock = csv.substring(csv.indexOf("Type,Count,Amount"));
            assertThat(typeBlock).contains("SERVICE_FEE");
            assertThat(typeBlock).contains("DEPOSIT");
        }
    }

    // ---------------------------------------------------------------------------
    // createSettlement
    // ---------------------------------------------------------------------------

    @Nested
    class CreateSettlement {

        @Test
        void success() {
            LocalDate start = LocalDate.of(2026, 4, 1);
            LocalDate end = LocalDate.of(2026, 4, 10);

            SettlementRequest request = new SettlementRequest();
            request.setPeriodStart(start);
            request.setPeriodEnd(end);
            request.setNotes("Monthly settlement");

            LocalDateTime dayStart = start.atStartOfDay();
            LocalDateTime dayEnd = end.plusDays(1).atStartOfDay();

            Transaction t1 = buildTransaction(1L, TransactionType.SERVICE_FEE, new BigDecimal("500.00"), "k1");
            Transaction t2 = buildTransaction(2L, TransactionType.DEPOSIT, new BigDecimal("300.00"), "k2");

            when(settlementRepository.existsByPeriodStartAndPeriodEnd(start, end)).thenReturn(false);
            when(transactionRepository.findByDay(dayStart, dayEnd)).thenReturn(List.of(t1, t2));
            when(refundRepository.sumRefundsByDay(dayStart, dayEnd)).thenReturn(new BigDecimal("100.00"));
            when(refundRepository.findByFilters(isNull(), eq(dayStart), eq(dayEnd), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(buildRefund(1L, 1L, new BigDecimal("100.00"), "r1"))));

            Settlement saved = buildSettlement(50L, start, end, SettlementStatus.DRAFT);
            saved.setTotalTransactions(new BigDecimal("800.00"));
            saved.setTotalRefunds(new BigDecimal("100.00"));
            saved.setNetAmount(new BigDecimal("700.00"));
            saved.setTransactionCount(2);
            saved.setRefundCount(1);
            saved.setNotes("Monthly settlement");
            when(settlementRepository.save(any(Settlement.class))).thenReturn(saved);

            SettlementResponse response = financialService.createSettlement(request, USER_ID);

            assertThat(response.getId()).isEqualTo(50L);
            assertThat(response.getStatus()).isEqualTo("DRAFT");
            assertThat(response.getTransactionCount()).isEqualTo(2);
            assertThat(response.getRefundCount()).isEqualTo(1);
            assertThat(response.getTotalTransactions()).isEqualByComparingTo("800.00");
            assertThat(response.getTotalRefunds()).isEqualByComparingTo("100.00");
            assertThat(response.getNetAmount()).isEqualByComparingTo("700.00");

            verify(settlementRepository).save(settlementCaptor.capture());
            Settlement captured = settlementCaptor.getValue();
            assertThat(captured.getPeriodStart()).isEqualTo(start);
            assertThat(captured.getPeriodEnd()).isEqualTo(end);
            assertThat(captured.getCreatedBy()).isEqualTo(USER_ID);
        }

        @Test
        void periodEndBeforeStartThrows() {
            SettlementRequest request = new SettlementRequest();
            request.setPeriodStart(LocalDate.of(2026, 4, 10));
            request.setPeriodEnd(LocalDate.of(2026, 4, 1));

            assertThatThrownBy(() -> financialService.createSettlement(request, USER_ID))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("Period end must be after period start");
        }

        @Test
        void duplicatePeriodThrows() {
            LocalDate start = LocalDate.of(2026, 4, 1);
            LocalDate end = LocalDate.of(2026, 4, 10);

            SettlementRequest request = new SettlementRequest();
            request.setPeriodStart(start);
            request.setPeriodEnd(end);

            when(settlementRepository.existsByPeriodStartAndPeriodEnd(start, end)).thenReturn(true);

            assertThatThrownBy(() -> financialService.createSettlement(request, USER_ID))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("Settlement already exists for this period");
        }

        @Test
        void auditLogged() {
            LocalDate start = LocalDate.of(2026, 4, 1);
            LocalDate end = LocalDate.of(2026, 4, 10);

            SettlementRequest request = new SettlementRequest();
            request.setPeriodStart(start);
            request.setPeriodEnd(end);

            when(settlementRepository.existsByPeriodStartAndPeriodEnd(start, end)).thenReturn(false);
            when(transactionRepository.findByDay(any(), any())).thenReturn(List.of());
            when(refundRepository.sumRefundsByDay(any(), any())).thenReturn(BigDecimal.ZERO);
            when(refundRepository.findByFilters(isNull(), any(), any(), any(Pageable.class)))
                    .thenReturn(Page.empty());

            Settlement saved = buildSettlement(51L, start, end, SettlementStatus.DRAFT);
            when(settlementRepository.save(any(Settlement.class))).thenReturn(saved);

            financialService.createSettlement(request, USER_ID);

            verify(auditService).log(
                    eq(USER_ID), isNull(), eq("FINANCIAL"), eq("CREATE"),
                    eq("Settlement"), eq(51L),
                    anyString(), isNull());
        }
    }

    // ---------------------------------------------------------------------------
    // confirmSettlement
    // ---------------------------------------------------------------------------

    @Nested
    class ConfirmSettlement {

        @Test
        void successFromDraft() {
            Settlement draft = buildSettlement(50L, LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 10), SettlementStatus.DRAFT);
            when(settlementRepository.findById(50L)).thenReturn(Optional.of(draft));

            Settlement confirmed = buildSettlement(50L, LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 10), SettlementStatus.CONFIRMED);
            when(settlementRepository.save(any(Settlement.class))).thenReturn(confirmed);

            SettlementResponse response = financialService.confirmSettlement(50L, USER_ID);

            assertThat(response.getStatus()).isEqualTo("CONFIRMED");

            verify(settlementRepository).save(settlementCaptor.capture());
            assertThat(settlementCaptor.getValue().getStatus()).isEqualTo(SettlementStatus.CONFIRMED);

            verify(auditService).log(
                    eq(USER_ID), isNull(), eq("FINANCIAL"), eq("STATE_CHANGE"),
                    eq("Settlement"), eq(50L),
                    eq("Confirmed settlement"), isNull());
        }

        @Test
        void notDraftThrows() {
            Settlement confirmed = buildSettlement(50L, LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 10), SettlementStatus.CONFIRMED);
            when(settlementRepository.findById(50L)).thenReturn(Optional.of(confirmed));

            assertThatThrownBy(() -> financialService.confirmSettlement(50L, USER_ID))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("Settlement is not in DRAFT status");
        }

        @Test
        void notFoundThrows() {
            when(settlementRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> financialService.confirmSettlement(999L, USER_ID))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Settlement not found");
        }
    }

    // ---------------------------------------------------------------------------
    // Mixed-currency report validation
    // ---------------------------------------------------------------------------

    @Nested
    class MixedCurrencyReport {

        @Test
        void singleCurrency_succeeds() {
            Transaction t1 = buildTransaction(1L, TransactionType.SERVICE_FEE, new BigDecimal("500"), "k1");
            Transaction t2 = buildTransaction(2L, TransactionType.SERVICE_FEE, new BigDecimal("300"), "k2");

            when(transactionRepository.findByDay(any(), any())).thenReturn(List.of(t1, t2));
            when(refundRepository.sumRefundsByDay(any(), any())).thenReturn(BigDecimal.ZERO);

            DailyReportResponse report = financialService.getDailyReport(LocalDate.now());
            assertThat(report.getCurrency()).isEqualTo("CNY");
            assertThat(report.getTotalAmount()).isEqualByComparingTo("800");
        }

        @Test
        void mixedCurrencies_throwsBusinessRuleException() {
            Transaction t1 = buildTransaction(1L, TransactionType.SERVICE_FEE, new BigDecimal("500"), "k1");
            t1.setCurrency("CNY");
            Transaction t2 = buildTransaction(2L, TransactionType.SERVICE_FEE, new BigDecimal("300"), "k2");
            t2.setCurrency("USD");

            when(transactionRepository.findByDay(any(), any())).thenReturn(List.of(t1, t2));
            when(refundRepository.sumRefundsByDay(any(), any())).thenReturn(BigDecimal.ZERO);

            assertThatThrownBy(() -> financialService.getDailyReport(LocalDate.now()))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("mixed currencies");
        }

        @Test
        void noTransactions_defaultsCNY() {
            when(transactionRepository.findByDay(any(), any())).thenReturn(List.of());
            when(refundRepository.sumRefundsByDay(any(), any())).thenReturn(BigDecimal.ZERO);

            DailyReportResponse report = financialService.getDailyReport(LocalDate.now());
            assertThat(report.getCurrency()).isEqualTo("CNY");
            assertThat(report.getTotalTransactions()).isZero();
        }
    }

    // ---------------------------------------------------------------------------
    // Cross-user idempotency
    // ---------------------------------------------------------------------------

    @Nested
    class CrossUserIdempotency {

        @Test
        void sameUserIdempotency_returnsExisting() {
            Transaction existing = buildTransaction(10L, TransactionType.SERVICE_FEE, new BigDecimal("200"), "key-1");
            when(transactionRepository.findByIdempotencyKey("key-1")).thenReturn(Optional.of(existing));

            TransactionRequest request = buildTransactionRequest("SERVICE_FEE", new BigDecimal("200"), "key-1");
            TransactionResponse response = financialService.createTransaction(request, USER_ID);

            assertThat(response.getId()).isEqualTo(10L);
        }

        @Test
        void differentUserIdempotency_throwsBusinessRuleException() {
            Transaction existing = buildTransaction(10L, TransactionType.SERVICE_FEE, new BigDecimal("200"), "key-1");
            when(transactionRepository.findByIdempotencyKey("key-1")).thenReturn(Optional.of(existing));

            TransactionRequest request = buildTransactionRequest("SERVICE_FEE", new BigDecimal("200"), "key-1");

            assertThatThrownBy(() -> financialService.createTransaction(request, 999L))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("Idempotency key already used");
        }
    }

    // ---------------------------------------------------------------------------
    // Invalid date parsing
    // ---------------------------------------------------------------------------

    @Nested
    class InvalidDateParsing {

        @Test
        void malformedDateFrom_throwsBusinessRuleException() {
            assertThatThrownBy(() -> financialService.listTransactions(null, null, null, "bad-date", null, null))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("Invalid date format");
        }

        @Test
        void malformedDateTo_throwsBusinessRuleException() {
            assertThatThrownBy(() -> financialService.listTransactions(null, null, null, null, "2026-99-99", null))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("Invalid date format");
        }
    }
}
