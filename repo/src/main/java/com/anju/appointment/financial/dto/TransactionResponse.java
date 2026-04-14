package com.anju.appointment.financial.dto;

import com.anju.appointment.financial.entity.Transaction;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Builder
public class TransactionResponse {

    private Long id;
    private Long appointmentId;
    private String type;
    private BigDecimal amount;
    private String currency;
    private String status;
    private String description;
    private String idempotencyKey;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static TransactionResponse fromEntity(Transaction transaction) {
        return TransactionResponse.builder()
                .id(transaction.getId())
                .appointmentId(transaction.getAppointmentId())
                .type(transaction.getType().name())
                .amount(transaction.getAmount())
                .currency(transaction.getCurrency())
                .status(transaction.getStatus())
                .description(transaction.getDescription())
                .idempotencyKey(transaction.getIdempotencyKey())
                .createdBy(transaction.getCreatedBy())
                .createdAt(transaction.getCreatedAt())
                .updatedAt(transaction.getUpdatedAt())
                .build();
    }
}
