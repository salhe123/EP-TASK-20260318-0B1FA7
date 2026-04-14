package com.anju.appointment.auth.service;

import com.anju.appointment.audit.service.AuditService;
import com.anju.appointment.auth.dto.CreateUserRequest;
import com.anju.appointment.auth.dto.UpdateUserRequest;
import com.anju.appointment.auth.dto.UserResponse;
import com.anju.appointment.auth.entity.Role;
import com.anju.appointment.auth.entity.User;
import com.anju.appointment.auth.repository.UserRepository;
import com.anju.appointment.common.BusinessRuleException;
import com.anju.appointment.common.ResourceNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthService authService;
    private final AuditService auditService;

    public UserService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       AuthService authService,
                       AuditService auditService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authService = authService;
        this.auditService = auditService;
    }

    @Transactional
    public UserResponse createUser(CreateUserRequest request, Long adminUserId) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new BusinessRuleException("Username already exists");
        }

        authService.validatePasswordStrength(request.getPassword());

        Role role;
        try {
            role = Role.valueOf(request.getRole());
        } catch (IllegalArgumentException e) {
            throw new BusinessRuleException("Invalid role: " + request.getRole());
        }

        User user = new User();
        user.setUsername(request.getUsername());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setFullName(request.getFullName());
        user.setRole(role);
        user.setPhone(request.getPhone() != null ? request.getPhone() : "");
        user.setEmail(request.getEmail() != null ? request.getEmail() : "");
        user.setForcePasswordReset(true);

        user = userRepository.save(user);
        auditService.log(adminUserId, null, "AUTH", "CREATE",
                "User", user.getId(), "Created user: " + user.getUsername() + " with role " + role.name(), null);
        return UserResponse.fromEntity(user);
    }

    public Page<UserResponse> listUsers(String roleFilter, Boolean enabledFilter, Pageable pageable) {
        Role role = null;
        if (roleFilter != null && !roleFilter.isBlank()) {
            try {
                role = Role.valueOf(roleFilter);
            } catch (IllegalArgumentException e) {
                throw new BusinessRuleException("Invalid role filter: " + roleFilter);
            }
        }

        return userRepository.findByFilters(role, enabledFilter, pageable)
                .map(UserResponse::fromEntity);
    }

    public UserResponse getUser(Long id) {
        User user = findUserOrThrow(id);
        return UserResponse.fromEntity(user);
    }

    @Transactional
    public UserResponse updateUser(Long id, UpdateUserRequest request, Long adminUserId) {
        User user = findUserOrThrow(id);

        if (request.getFullName() != null) {
            user.setFullName(request.getFullName());
        }
        if (request.getRole() != null) {
            try {
                user.setRole(Role.valueOf(request.getRole()));
            } catch (IllegalArgumentException e) {
                throw new BusinessRuleException("Invalid role: " + request.getRole());
            }
        }
        if (request.getPhone() != null) {
            user.setPhone(request.getPhone());
        }
        if (request.getEmail() != null) {
            user.setEmail(request.getEmail());
        }

        user = userRepository.save(user);
        auditService.log(adminUserId, null, "AUTH", "UPDATE",
                "User", user.getId(), "Updated user: " + user.getUsername(), null);
        return UserResponse.fromEntity(user);
    }

    @Transactional
    public UserResponse disableUser(Long id, Long adminUserId) {
        User user = findUserOrThrow(id);
        user.setEnabled(false);
        user = userRepository.save(user);
        auditService.log(adminUserId, null, "AUTH", "UPDATE",
                "User", user.getId(), "Disabled user: " + user.getUsername(), null);
        return UserResponse.fromEntity(user);
    }

    @Transactional
    public UserResponse enableUser(Long id, Long adminUserId) {
        User user = findUserOrThrow(id);
        user.setEnabled(true);
        user = userRepository.save(user);
        auditService.log(adminUserId, null, "AUTH", "UPDATE",
                "User", user.getId(), "Enabled user: " + user.getUsername(), null);
        return UserResponse.fromEntity(user);
    }

    @Transactional
    public UserResponse unlockUser(Long id, Long adminUserId) {
        User user = findUserOrThrow(id);
        user.setLocked(false);
        user.setFailedLoginAttempts(0);
        user.setLockExpiresAt(null);
        user = userRepository.save(user);
        auditService.log(adminUserId, null, "AUTH", "UPDATE",
                "User", user.getId(), "Unlocked user: " + user.getUsername(), null);
        return UserResponse.fromEntity(user);
    }

    @Transactional
    public void resetPassword(Long id, String newPassword, Long adminUserId) {
        User user = findUserOrThrow(id);
        authService.validatePasswordStrength(newPassword);
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setForcePasswordReset(true);
        userRepository.save(user);
        auditService.log(adminUserId, null, "AUTH", "UPDATE",
                "User", user.getId(), "Reset password for user: " + user.getUsername(), null);
    }

    private User findUserOrThrow(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));
    }
}
