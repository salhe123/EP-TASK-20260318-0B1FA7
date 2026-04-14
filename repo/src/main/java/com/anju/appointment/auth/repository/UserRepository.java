package com.anju.appointment.auth.repository;

import com.anju.appointment.auth.entity.Role;
import com.anju.appointment.auth.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);

    boolean existsByUsername(String username);

    @Query("SELECT u FROM User u WHERE (:role IS NULL OR u.role = :role) " +
           "AND (:enabled IS NULL OR u.enabled = :enabled)")
    Page<User> findByFilters(@Param("role") Role role,
                             @Param("enabled") Boolean enabled,
                             Pageable pageable);
}
