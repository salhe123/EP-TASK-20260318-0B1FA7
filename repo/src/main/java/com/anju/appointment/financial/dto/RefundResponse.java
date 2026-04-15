package com.anju.appointment.financial.dto;

import com.anju.appointment.financial.entity.Refund;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Builder
public class RefundResponse {

    private Long id;
    private Long originalTransactionId;
    private BigDecimal amount;
    private String reason;
    private String status;
    private LocalDateTime createdAt;

    public static RefundResponse fromEntity(Refund refund) {
        return RefundResponse.builder()
                .id(refund.getId())
                .originalTransactionId(refund.getOriginalTransactionId())
                .amount(refund.getAmount())
                .reason(refund.getReason())
                .status(refund.getStatus())
                .createdAt(refund.getCreatedAt())
                .build();
    }
}
