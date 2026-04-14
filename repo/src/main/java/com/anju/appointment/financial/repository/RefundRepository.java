package com.anju.appointment.financial.repository;

import com.anju.appointment.financial.entity.Refund;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

public interface RefundRepository extends JpaRepository<Refund, Long> {

    Optional<Refund> findByIdempotencyKey(String idempotencyKey);

    @Query("SELECT COALESCE(SUM(r.amount), 0) FROM Refund r WHERE r.originalTransactionId = :transactionId")
    BigDecimal sumRefundsByTransactionId(@Param("transactionId") Long transactionId);

    @Query("SELECT r FROM Refund r WHERE " +
           "(:transactionId IS NULL OR r.originalTransactionId = :transactionId) AND " +
           "(:dateFrom IS NULL OR r.createdAt >= :dateFrom) AND " +
           "(:dateTo IS NULL OR r.createdAt <= :dateTo)")
    Page<Refund> findByFilters(@Param("transactionId") Long transactionId,
                                @Param("dateFrom") LocalDateTime dateFrom,
                                @Param("dateTo") LocalDateTime dateTo,
                                Pageable pageable);

    @Query("SELECT COALESCE(SUM(r.amount), 0) FROM Refund r WHERE r.createdAt >= :dayStart AND r.createdAt < :dayEnd")
    BigDecimal sumRefundsByDay(@Param("dayStart") LocalDateTime dayStart,
                               @Param("dayEnd") LocalDateTime dayEnd);
}
