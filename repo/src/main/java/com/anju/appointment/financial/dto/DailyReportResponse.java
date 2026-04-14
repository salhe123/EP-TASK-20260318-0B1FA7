package com.anju.appointment.financial.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
public class DailyReportResponse {

    private LocalDate date;
    private int totalTransactions;
    private BigDecimal totalAmount;
    private BigDecimal totalRefunds;
    private BigDecimal netAmount;
    private String currency;
    private List<TypeSummary> byType;
    private LocalDateTime generatedAt;

    @Getter
    @Builder
    public static class TypeSummary {
        private String type;
        private int count;
        private BigDecimal amount;
    }
}
