package com.anju.appointment.financial.service;

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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class FinancialService {

    private final TransactionRepository transactionRepository;
    private final RefundRepository refundRepository;
    private final SettlementRepository settlementRepository;
    private final AppointmentRepository appointmentRepository;
    private final AuditService auditService;

    public FinancialService(TransactionRepository transactionRepository,
                            RefundRepository refundRepository,
                            SettlementRepository settlementRepository,
                            AppointmentRepository appointmentRepository,
                            AuditService auditService) {
        this.transactionRepository = transactionRepository;
        this.refundRepository = refundRepository;
        this.settlementRepository = settlementRepository;
        this.appointmentRepository = appointmentRepository;
        this.auditService = auditService;
    }

    @Transactional
    public TransactionResponse createTransaction(TransactionRequest request, Long createdBy) {
        Optional<Transaction> existing = transactionRepository.findByIdempotencyKey(request.getIdempotencyKey());
        if (existing.isPresent()) {
            return TransactionResponse.fromEntity(existing.get());
        }

        if (!appointmentRepository.existsById(request.getAppointmentId())) {
            throw new ResourceNotFoundException("Appointment not found with id: " + request.getAppointmentId());
        }

        TransactionType type;
        try {
            type = TransactionType.valueOf(request.getType());
        } catch (IllegalArgumentException e) {
            throw new BusinessRuleException("Invalid transaction type: " + request.getType());
        }

        Transaction transaction = new Transaction();
        transaction.setAppointmentId(request.getAppointmentId());
        transaction.setType(type);
        transaction.setAmount(request.getAmount());
        transaction.setCurrency(request.getCurrency() != null ? request.getCurrency() : "CNY");
        transaction.setDescription(request.getDescription());
        transaction.setIdempotencyKey(request.getIdempotencyKey());
        transaction.setCreatedBy(createdBy);

        transaction = transactionRepository.save(transaction);
        auditService.log(createdBy, null, "FINANCIAL", "CREATE",
                "Transaction", transaction.getId(),
                "Created " + type.name() + " transaction: " + transaction.getAmount() + " " + transaction.getCurrency(),
                null);
        return TransactionResponse.fromEntity(transaction);
    }

    public Page<TransactionResponse> listTransactions(Long appointmentId, String typeFilter,
                                                       String status, String dateFrom, String dateTo,
                                                       Pageable pageable) {
        TransactionType type = null;
        if (typeFilter != null && !typeFilter.isBlank()) {
            try {
                type = TransactionType.valueOf(typeFilter);
            } catch (IllegalArgumentException e) {
                throw new BusinessRuleException("Invalid transaction type: " + typeFilter);
            }
        }

        LocalDateTime from = dateFrom != null ? LocalDate.parse(dateFrom).atStartOfDay() : null;
        LocalDateTime to = dateTo != null ? LocalDate.parse(dateTo).atTime(23, 59, 59) : null;

        return transactionRepository.findByFilters(appointmentId, type, status, from, to, pageable)
                .map(TransactionResponse::fromEntity);
    }

    public TransactionResponse getTransaction(Long id) {
        Transaction transaction = transactionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found with id: " + id));
        return TransactionResponse.fromEntity(transaction);
    }

    @Transactional
    public RefundResponse createRefund(RefundRequest request, Long createdBy) {
        Optional<Refund> existing = refundRepository.findByIdempotencyKey(request.getIdempotencyKey());
        if (existing.isPresent()) {
            return RefundResponse.fromEntity(existing.get());
        }

        Transaction originalTransaction = transactionRepository.findById(request.getOriginalTransactionId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Original transaction not found with id: " + request.getOriginalTransactionId()));

        BigDecimal totalRefunded = refundRepository.sumRefundsByTransactionId(request.getOriginalTransactionId());
        BigDecimal newTotal = totalRefunded.add(request.getAmount());

        if (newTotal.compareTo(originalTransaction.getAmount()) > 0) {
            throw new BusinessRuleException("Refund amount exceeds original transaction amount. " +
                    "Already refunded: " + totalRefunded + ", requested: " + request.getAmount() +
                    ", original: " + originalTransaction.getAmount());
        }

        Refund refund = new Refund();
        refund.setOriginalTransactionId(request.getOriginalTransactionId());
        refund.setAmount(request.getAmount());
        refund.setReason(request.getReason());
        refund.setIdempotencyKey(request.getIdempotencyKey());
        refund.setCreatedBy(createdBy);

        refund = refundRepository.save(refund);
        auditService.log(createdBy, null, "FINANCIAL", "CREATE",
                "Refund", refund.getId(),
                "Created refund: " + refund.getAmount() + " against transaction " + refund.getOriginalTransactionId(),
                null);
        return RefundResponse.fromEntity(refund);
    }

    public Page<RefundResponse> listRefunds(Long transactionId, String dateFrom, String dateTo,
                                             Pageable pageable) {
        LocalDateTime from = dateFrom != null ? LocalDate.parse(dateFrom).atStartOfDay() : null;
        LocalDateTime to = dateTo != null ? LocalDate.parse(dateTo).atTime(23, 59, 59) : null;

        return refundRepository.findByFilters(transactionId, from, to, pageable)
                .map(RefundResponse::fromEntity);
    }

    public DailyReportResponse getDailyReport(LocalDate date) {
        LocalDateTime dayStart = date.atStartOfDay();
        LocalDateTime dayEnd = date.plusDays(1).atStartOfDay();

        List<Transaction> transactions = transactionRepository.findByDay(dayStart, dayEnd);
        BigDecimal totalRefunds = refundRepository.sumRefundsByDay(dayStart, dayEnd);

        BigDecimal totalAmount = transactions.stream()
                .map(Transaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<TransactionType, List<Transaction>> byType = transactions.stream()
                .collect(Collectors.groupingBy(Transaction::getType));

        List<DailyReportResponse.TypeSummary> typeSummaries = byType.entrySet().stream()
                .map(entry -> DailyReportResponse.TypeSummary.builder()
                        .type(entry.getKey().name())
                        .count(entry.getValue().size())
                        .amount(entry.getValue().stream()
                                .map(Transaction::getAmount)
                                .reduce(BigDecimal.ZERO, BigDecimal::add))
                        .build())
                .toList();

        return DailyReportResponse.builder()
                .date(date)
                .totalTransactions(transactions.size())
                .totalAmount(totalAmount)
                .totalRefunds(totalRefunds)
                .netAmount(totalAmount.subtract(totalRefunds))
                .currency("CNY")
                .byType(typeSummaries)
                .generatedAt(LocalDateTime.now())
                .build();
    }

    public String exportDailyReportCsv(LocalDate date) {
        DailyReportResponse report = getDailyReport(date);
        StringBuilder csv = new StringBuilder();
        csv.append("Date,Total Transactions,Total Amount,Total Refunds,Net Amount,Currency\n");
        csv.append(report.getDate()).append(",")
                .append(report.getTotalTransactions()).append(",")
                .append(report.getTotalAmount()).append(",")
                .append(report.getTotalRefunds()).append(",")
                .append(report.getNetAmount()).append(",")
                .append(report.getCurrency()).append("\n");

        if (report.getByType() != null && !report.getByType().isEmpty()) {
            csv.append("\nType,Count,Amount\n");
            for (DailyReportResponse.TypeSummary ts : report.getByType()) {
                csv.append(ts.getType()).append(",")
                        .append(ts.getCount()).append(",")
                        .append(ts.getAmount()).append("\n");
            }
        }
        return csv.toString();
    }

    @Transactional
    public SettlementResponse createSettlement(SettlementRequest request, Long createdBy) {
        if (request.getPeriodEnd().isBefore(request.getPeriodStart())) {
            throw new BusinessRuleException("Period end must be after period start");
        }
        if (settlementRepository.existsByPeriodStartAndPeriodEnd(
                request.getPeriodStart(), request.getPeriodEnd())) {
            throw new BusinessRuleException("Settlement already exists for this period");
        }

        LocalDateTime start = request.getPeriodStart().atStartOfDay();
        LocalDateTime end = request.getPeriodEnd().plusDays(1).atStartOfDay();

        List<Transaction> transactions = transactionRepository.findByDay(start, end);
        BigDecimal totalRefunds = refundRepository.sumRefundsByDay(start, end);
        long refundCount = refundRepository.findByFilters(null, start, end,
                Pageable.unpaged()).getTotalElements();

        BigDecimal totalAmount = transactions.stream()
                .map(Transaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Settlement settlement = new Settlement();
        settlement.setPeriodStart(request.getPeriodStart());
        settlement.setPeriodEnd(request.getPeriodEnd());
        settlement.setTotalTransactions(totalAmount);
        settlement.setTotalRefunds(totalRefunds);
        settlement.setNetAmount(totalAmount.subtract(totalRefunds));
        settlement.setTransactionCount(transactions.size());
        settlement.setRefundCount((int) refundCount);
        settlement.setNotes(request.getNotes());
        settlement.setCreatedBy(createdBy);

        settlement = settlementRepository.save(settlement);
        auditService.log(createdBy, null, "FINANCIAL", "CREATE",
                "Settlement", settlement.getId(),
                "Created settlement for period " + request.getPeriodStart() + " to " + request.getPeriodEnd(),
                null);
        return SettlementResponse.fromEntity(settlement);
    }

    @Transactional
    public SettlementResponse confirmSettlement(Long id, Long userId) {
        Settlement settlement = settlementRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Settlement not found with id: " + id));
        if (settlement.getStatus() != SettlementStatus.DRAFT) {
            throw new BusinessRuleException("Settlement is not in DRAFT status");
        }
        settlement.setStatus(SettlementStatus.CONFIRMED);
        settlement = settlementRepository.save(settlement);
        auditService.log(userId, null, "FINANCIAL", "STATE_CHANGE",
                "Settlement", settlement.getId(), "Confirmed settlement", null);
        return SettlementResponse.fromEntity(settlement);
    }

    public SettlementResponse getSettlement(Long id) {
        Settlement settlement = settlementRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Settlement not found with id: " + id));
        return SettlementResponse.fromEntity(settlement);
    }

    public Page<SettlementResponse> listSettlements(String statusFilter, String dateFrom,
                                                      String dateTo, Pageable pageable) {
        SettlementStatus status = null;
        if (statusFilter != null && !statusFilter.isBlank()) {
            try {
                status = SettlementStatus.valueOf(statusFilter);
            } catch (IllegalArgumentException e) {
                throw new BusinessRuleException("Invalid settlement status: " + statusFilter);
            }
        }
        LocalDate from = dateFrom != null ? LocalDate.parse(dateFrom) : null;
        LocalDate to = dateTo != null ? LocalDate.parse(dateTo) : null;
        return settlementRepository.findByFilters(status, from, to, pageable)
                .map(SettlementResponse::fromEntity);
    }
}
