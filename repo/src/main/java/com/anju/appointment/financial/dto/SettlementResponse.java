package com.anju.appointment.financial.dto;

import com.anju.appointment.financial.entity.Settlement;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Builder
public class SettlementResponse {

    private Long id;
    private LocalDate periodStart;
    private LocalDate periodEnd;
    private BigDecimal totalTransactions;
    private BigDecimal totalRefunds;
    private BigDecimal netAmount;
    private int transactionCount;
    private int refundCount;
    private String currency;
    private String status;
    private String notes;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static SettlementResponse fromEntity(Settlement settlement) {
        return SettlementResponse.builder()
                .id(settlement.getId())
                .periodStart(settlement.getPeriodStart())
                .periodEnd(settlement.getPeriodEnd())
                .totalTransactions(settlement.getTotalTransactions())
                .totalRefunds(settlement.getTotalRefunds())
                .netAmount(settlement.getNetAmount())
                .transactionCount(settlement.getTransactionCount())
                .refundCount(settlement.getRefundCount())
                .currency(settlement.getCurrency())
                .status(settlement.getStatus().name())
                .notes(settlement.getNotes())
                .createdAt(settlement.getCreatedAt())
                .updatedAt(settlement.getUpdatedAt())
                .build();
    }
}
