package com.anju.appointment.financial.entity;

import com.anju.appointment.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "settlements", indexes = {
        @Index(name = "idx_settlement_period", columnList = "periodStart, periodEnd"),
        @Index(name = "idx_settlement_status", columnList = "status")
})
@Getter
@Setter
@NoArgsConstructor
public class Settlement extends BaseEntity {

    @Column(nullable = false)
    private LocalDate periodStart;

    @Column(nullable = false)
    private LocalDate periodEnd;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal totalTransactions;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal totalRefunds;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal netAmount;

    @Column(nullable = false)
    private int transactionCount;

    @Column(nullable = false)
    private int refundCount;

    @Column(nullable = false)
    private String currency = "CNY";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SettlementStatus status = SettlementStatus.DRAFT;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(nullable = false)
    private Long createdBy;
}
