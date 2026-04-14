package com.anju.appointment.financial.repository;

import com.anju.appointment.financial.entity.Settlement;
import com.anju.appointment.financial.entity.SettlementStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;

public interface SettlementRepository extends JpaRepository<Settlement, Long> {

    boolean existsByPeriodStartAndPeriodEnd(LocalDate periodStart, LocalDate periodEnd);

    @Query("SELECT s FROM Settlement s WHERE " +
           "(:status IS NULL OR s.status = :status) AND " +
           "(:dateFrom IS NULL OR s.periodStart >= :dateFrom) AND " +
           "(:dateTo IS NULL OR s.periodEnd <= :dateTo)")
    Page<Settlement> findByFilters(@Param("status") SettlementStatus status,
                                    @Param("dateFrom") LocalDate dateFrom,
                                    @Param("dateTo") LocalDate dateTo,
                                    Pageable pageable);
}
