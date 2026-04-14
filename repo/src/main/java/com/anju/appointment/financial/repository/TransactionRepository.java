package com.anju.appointment.financial.repository;

import com.anju.appointment.financial.entity.Transaction;
import com.anju.appointment.financial.entity.TransactionType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    Optional<Transaction> findByIdempotencyKey(String idempotencyKey);

    @Query("SELECT t FROM Transaction t WHERE " +
           "(:appointmentId IS NULL OR t.appointmentId = :appointmentId) AND " +
           "(:type IS NULL OR t.type = :type) AND " +
           "(:status IS NULL OR t.status = :status) AND " +
           "(:dateFrom IS NULL OR t.createdAt >= :dateFrom) AND " +
           "(:dateTo IS NULL OR t.createdAt <= :dateTo)")
    Page<Transaction> findByFilters(@Param("appointmentId") Long appointmentId,
                                     @Param("type") TransactionType type,
                                     @Param("status") String status,
                                     @Param("dateFrom") LocalDateTime dateFrom,
                                     @Param("dateTo") LocalDateTime dateTo,
                                     Pageable pageable);

    @Query("SELECT t FROM Transaction t WHERE t.createdAt >= :dayStart AND t.createdAt < :dayEnd")
    List<Transaction> findByDay(@Param("dayStart") LocalDateTime dayStart,
                                @Param("dayEnd") LocalDateTime dayEnd);
}
