package com.anju.appointment.financial.controller;

import com.anju.appointment.auth.security.AuthenticatedUser;
import com.anju.appointment.financial.dto.DailyReportResponse;
import com.anju.appointment.financial.dto.RefundRequest;
import com.anju.appointment.financial.dto.RefundResponse;
import com.anju.appointment.financial.dto.SettlementRequest;
import com.anju.appointment.financial.dto.SettlementResponse;
import com.anju.appointment.financial.dto.TransactionRequest;
import com.anju.appointment.financial.dto.TransactionResponse;
import com.anju.appointment.financial.service.FinancialService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/financial")
@PreAuthorize("hasAnyRole('FINANCE', 'ADMIN')")
public class FinancialController {

    private final FinancialService financialService;

    public FinancialController(FinancialService financialService) {
        this.financialService = financialService;
    }

    @PostMapping("/transactions")
    public ResponseEntity<TransactionResponse> createTransaction(
            @Valid @RequestBody TransactionRequest request,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return ResponseEntity.ok(financialService.createTransaction(request, principal.getUserId()));
    }

    @GetMapping("/transactions")
    public ResponseEntity<Page<TransactionResponse>> listTransactions(
            @RequestParam(required = false) Long appointmentId,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(financialService.listTransactions(
                appointmentId, type, status, dateFrom, dateTo, pageable));
    }

    @GetMapping("/transactions/{id}")
    public ResponseEntity<TransactionResponse> getTransaction(@PathVariable Long id) {
        return ResponseEntity.ok(financialService.getTransaction(id));
    }

    @PostMapping("/refunds")
    public ResponseEntity<RefundResponse> createRefund(
            @Valid @RequestBody RefundRequest request,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return ResponseEntity.ok(financialService.createRefund(request, principal.getUserId()));
    }

    @GetMapping("/refunds")
    public ResponseEntity<Page<RefundResponse>> listRefunds(
            @RequestParam(required = false) Long originalTransactionId,
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(financialService.listRefunds(originalTransactionId, dateFrom, dateTo, pageable));
    }

    @GetMapping("/reports/daily")
    public ResponseEntity<DailyReportResponse> getDailyReport(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(financialService.getDailyReport(date));
    }

    @GetMapping("/reports/daily/export")
    public ResponseEntity<byte[]> exportDailyReport(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        String csv = financialService.exportDailyReportCsv(date);
        String filename = "daily-report-" + date + ".csv";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(csv.getBytes());
    }

    @PostMapping("/settlements")
    public ResponseEntity<SettlementResponse> createSettlement(
            @Valid @RequestBody SettlementRequest request,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return ResponseEntity.ok(financialService.createSettlement(request, principal.getUserId()));
    }

    @PutMapping("/settlements/{id}/confirm")
    public ResponseEntity<SettlementResponse> confirmSettlement(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return ResponseEntity.ok(financialService.confirmSettlement(id, principal.getUserId()));
    }

    @GetMapping("/settlements/{id}")
    public ResponseEntity<SettlementResponse> getSettlement(@PathVariable Long id) {
        return ResponseEntity.ok(financialService.getSettlement(id));
    }

    @GetMapping("/settlements")
    public ResponseEntity<Page<SettlementResponse>> listSettlements(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(financialService.listSettlements(status, dateFrom, dateTo, pageable));
    }
}
