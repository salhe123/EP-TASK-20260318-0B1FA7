package com.anju.appointment.auth.service;

import com.anju.appointment.audit.service.AuditService;
import com.anju.appointment.auth.dto.ChangePasswordRequest;
import com.anju.appointment.auth.dto.LoginRequest;
import com.anju.appointment.auth.dto.LoginResponse;
import com.anju.appointment.auth.entity.RefreshToken;
import com.anju.appointment.auth.entity.Role;
import com.anju.appointment.auth.entity.User;
import com.anju.appointment.auth.repository.RefreshTokenRepository;
import com.anju.appointment.auth.repository.UserRepository;
import com.anju.appointment.auth.security.JwtProvider;
import com.anju.appointment.common.BusinessRuleException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.regex.Pattern;

@Service
public class AuthService implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final int LOCK_DURATION_MINUTES = 15;
    private static final int MIN_PASSWORD_LENGTH = 12;
    private static final Pattern PASSWORD_PATTERN =
            Pattern.compile("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^a-zA-Z\\d]).{12,}$");

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;
    private final AuditService auditService;
    private final String bootstrapAdminPassword;

    public AuthService(UserRepository userRepository,
                       RefreshTokenRepository refreshTokenRepository,
                       PasswordEncoder passwordEncoder,
                       JwtProvider jwtProvider,
                       AuditService auditService,
                       @org.springframework.beans.factory.annotation.Value("${app.security.bootstrap-admin-password:}") String bootstrapAdminPassword) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtProvider = jwtProvider;
        this.auditService = auditService;
        this.bootstrapAdminPassword = bootstrapAdminPassword;
    }

    @Override
    public void run(String... args) {
        if (userRepository.count() == 0) {
            if (bootstrapAdminPassword == null || bootstrapAdminPassword.isBlank()) {
                log.warn("No bootstrap admin password set (APP_SECURITY_BOOTSTRAP_ADMIN_PASSWORD). "
                        + "Skipping default admin creation. Set the env variable to bootstrap.");
                return;
            }
            User admin = new User();
            admin.setUsername("admin");
            admin.setPasswordHash(passwordEncoder.encode(bootstrapAdminPassword));
            admin.setFullName("System Administrator");
            admin.setRole(Role.ADMIN);
            admin.setPhone("");
            admin.setEmail("");
            admin.setForcePasswordReset(true);
            userRepository.save(admin);
            log.info("Default admin user created (username: admin, password must be changed on first login)");
        }
    }

    @Transactional(noRollbackFor = BusinessRuleException.class)
    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByUsername(request.getUsername()).orElse(null);

        if (user == null) {
            throw new BusinessRuleException("Invalid credentials");
        }

        if (!user.isEnabled()) {
            throw new BusinessRuleException("Account is disabled");
        }

        if (user.isLocked()) {
            if (user.getLockExpiresAt() != null && user.getLockExpiresAt().isAfter(LocalDateTime.now())) {
                throw new BusinessRuleException("Account is locked. Try again later");
            }
            user.setLocked(false);
            user.setFailedLoginAttempts(0);
            user.setLockExpiresAt(null);
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            int attempts = user.getFailedLoginAttempts() + 1;
            user.setFailedLoginAttempts(attempts);
            if (attempts >= MAX_FAILED_ATTEMPTS) {
                user.setLocked(true);
                user.setLockExpiresAt(LocalDateTime.now().plusMinutes(LOCK_DURATION_MINUTES));
                userRepository.save(user);
                auditService.log(user.getId(), user.getUsername(), "AUTH", "LOGIN_FAILED",
                        "User", user.getId(), "Account locked after " + MAX_FAILED_ATTEMPTS + " failed attempts", null);
                throw new BusinessRuleException("Account is locked due to too many failed attempts");
            }
            userRepository.save(user);
            auditService.log(user.getId(), user.getUsername(), "AUTH", "LOGIN_FAILED",
                    "User", user.getId(), "Failed login attempt " + attempts, null);
            throw new BusinessRuleException("Invalid credentials");
        }

        user.setFailedLoginAttempts(0);
        userRepository.save(user);
        auditService.log(user.getId(), user.getUsername(), "AUTH", "LOGIN",
                "User", user.getId(), "Successful login", null);

        String accessToken = jwtProvider.generateAccessToken(user.getId(), user.getUsername(), user.getRole().name());
        String refreshTokenValue = jwtProvider.generateRefreshToken(user.getId());

        refreshTokenRepository.revokeAllByUserId(user.getId());

        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setToken(refreshTokenValue);
        refreshToken.setUserId(user.getId());
        refreshToken.setExpiresAt(LocalDateTime.now().plusSeconds(jwtProvider.getRefreshTokenExpirySeconds()));
        refreshTokenRepository.save(refreshToken);

        auditService.log(user.getId(), user.getUsername(), "AUTH", "LOGIN",
                "User", user.getId(), "User logged in", null);

        return LoginResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshTokenValue)
                .forcePasswordReset(user.isForcePasswordReset())
                .role(user.getRole().name())
                .build();
    }

    @Transactional
    public LoginResponse refresh(String refreshTokenValue) {
        if (refreshTokenValue == null || refreshTokenValue.isBlank()) {
            throw new BusinessRuleException("No refresh token provided");
        }

        RefreshToken storedToken = refreshTokenRepository.findByTokenAndRevokedFalse(refreshTokenValue)
                .orElseThrow(() -> new BusinessRuleException("Invalid or expired refresh token"));

        if (storedToken.getExpiresAt().isBefore(LocalDateTime.now())) {
            storedToken.setRevoked(true);
            refreshTokenRepository.save(storedToken);
            throw new BusinessRuleException("Refresh token expired");
        }

        User user = userRepository.findById(storedToken.getUserId())
                .orElseThrow(() -> new BusinessRuleException("User not found"));

        if (!user.isEnabled()) {
            throw new BusinessRuleException("Account is disabled");
        }
        if (user.isLocked() && user.getLockExpiresAt() != null && user.getLockExpiresAt().isAfter(LocalDateTime.now())) {
            throw new BusinessRuleException("Account is locked");
        }

        storedToken.setRevoked(true);
        refreshTokenRepository.save(storedToken);

        String newAccessToken = jwtProvider.generateAccessToken(user.getId(), user.getUsername(), user.getRole().name());
        String newRefreshTokenValue = jwtProvider.generateRefreshToken(user.getId());

        RefreshToken newRefreshToken = new RefreshToken();
        newRefreshToken.setToken(newRefreshTokenValue);
        newRefreshToken.setUserId(user.getId());
        newRefreshToken.setExpiresAt(LocalDateTime.now().plusSeconds(jwtProvider.getRefreshTokenExpirySeconds()));
        refreshTokenRepository.save(newRefreshToken);

        return LoginResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRefreshTokenValue)
                .forcePasswordReset(user.isForcePasswordReset())
                .role(user.getRole().name())
                .build();
    }

    @Transactional
    public void logout(Long userId) {
        refreshTokenRepository.revokeAllByUserId(userId);
        auditService.log(userId, null, "AUTH", "LOGOUT",
                "User", userId, "User logged out", null);
    }

    @Transactional
    public void changePassword(Long userId, ChangePasswordRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessRuleException("User not found"));

        if (!passwordEncoder.matches(request.getOldPassword(), user.getPasswordHash())) {
            throw new BusinessRuleException("Current password is incorrect");
        }

        validatePasswordStrength(request.getNewPassword());

        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        user.setForcePasswordReset(false);
        userRepository.save(user);
        auditService.log(userId, null, "AUTH", "CHANGE_PASSWORD",
                "User", userId, "Password changed", null);
    }

    public void validatePasswordStrength(String password) {
        if (password == null || password.length() < MIN_PASSWORD_LENGTH) {
            throw new BusinessRuleException("Password must be at least " + MIN_PASSWORD_LENGTH + " characters");
        }
        if (!PASSWORD_PATTERN.matcher(password).matches()) {
            throw new BusinessRuleException("Password must contain uppercase, lowercase, digit, and special character");
        }
    }
}
