package com.anju.appointment.property.repository;

import com.anju.appointment.property.entity.Property;
import com.anju.appointment.property.entity.PropertyStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PropertyRepository extends JpaRepository<Property, Long> {

    @Query("SELECT p FROM Property p WHERE " +
           "(:type IS NULL OR p.type = :type) AND " +
           "(:status IS NULL OR p.status = :status) AND " +
           "(:keyword IS NULL OR LOWER(p.name) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "OR LOWER(p.address) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    Page<Property> findByFilters(@Param("type") String type,
                                  @Param("status") PropertyStatus status,
                                  @Param("keyword") String keyword,
                                  Pageable pageable);
}
