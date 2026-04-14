package com.anju.appointment.auth;

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
import com.anju.appointment.auth.service.AuthService;
import com.anju.appointment.common.BusinessRuleException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtProvider jwtProvider;

    @Mock
    private AuditService auditService;

    @InjectMocks
    private AuthService authService;

    private User createEnabledUser() {
        User user = new User();
        user.setId(1L);
        user.setUsername("testuser");
        user.setPasswordHash("hashedPassword");
        user.setFullName("Test User");
        user.setRole(Role.REVIEWER);
        user.setPhone("123456");
        user.setEmail("test@example.com");
        user.setEnabled(true);
        user.setLocked(false);
        user.setFailedLoginAttempts(0);
        user.setForcePasswordReset(false);
        return user;
    }

    private LoginRequest createLoginRequest(String username, String password) {
        LoginRequest request = new LoginRequest();
        request.setUsername(username);
        request.setPassword(password);
        return request;
    }

    private ChangePasswordRequest createChangePasswordRequest(String oldPassword, String newPassword) {
        ChangePasswordRequest request = new ChangePasswordRequest();
        request.setOldPassword(oldPassword);
        request.setNewPassword(newPassword);
        return request;
    }

    // ===========================
    // login() tests
    // ===========================

    @Nested
    @DisplayName("login")
    class LoginTests {

        @Test
        @DisplayName("success - returns tokens and resets failure count")
        void login_success_returnsTokensAndResetsFailureCount() {
            User user = createEnabledUser();
            user.setFailedLoginAttempts(3);
            LoginRequest request = createLoginRequest("testuser", "correctPass");

            when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(user));
            when(passwordEncoder.matches("correctPass", "hashedPassword")).thenReturn(true);
            when(jwtProvider.generateAccessToken(1L, "testuser", "REVIEWER")).thenReturn("access-token");
            when(jwtProvider.generateRefreshToken(1L)).thenReturn("refresh-token");
            when(jwtProvider.getRefreshTokenExpirySeconds()).thenReturn(86400L);

            LoginResponse response = authService.login(request);

            assertNotNull(response);
            assertEquals("access-token", response.getAccessToken());
            assertEquals("refresh-token", response.getRefreshToken());
            assertEquals("REVIEWER", response.getRole());
            assertFalse(response.isForcePasswordReset());

            // Verify failure count was reset
            assertEquals(0, user.getFailedLoginAttempts());
            verify(userRepository).save(user);

            // Verify old refresh tokens revoked and new one saved
            verify(refreshTokenRepository).revokeAllByUserId(1L);
            ArgumentCaptor<RefreshToken> tokenCaptor = ArgumentCaptor.forClass(RefreshToken.class);
            verify(refreshTokenRepository).save(tokenCaptor.capture());
            RefreshToken savedToken = tokenCaptor.getValue();
            assertEquals("refresh-token", savedToken.getToken());
            assertEquals(1L, savedToken.getUserId());
            assertFalse(savedToken.isRevoked());

            // Verify audit logging for successful login
            verify(auditService).log(eq(1L), eq("testuser"), eq("AUTH"), eq("LOGIN"),
                    eq("User"), eq(1L), eq("Successful login"), isNull());
        }

        @Test
        @DisplayName("success - returns forcePasswordReset flag when set")
        void login_success_returnsForcePasswordReset() {
            User user = createEnabledUser();
            user.setForcePasswordReset(true);
            LoginRequest request = createLoginRequest("testuser", "correctPass");

            when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(user));
            when(passwordEncoder.matches("correctPass", "hashedPassword")).thenReturn(true);
            when(jwtProvider.generateAccessToken(1L, "testuser", "REVIEWER")).thenReturn("at");
            when(jwtProvider.generateRefreshToken(1L)).thenReturn("rt");
            when(jwtProvider.getRefreshTokenExpirySeconds()).thenReturn(86400L);

            LoginResponse response = authService.login(request);

            assertTrue(response.isForcePasswordReset());
        }

        @Test
        @DisplayName("invalid username - throws BusinessRuleException")
        void login_invalidUsername_throwsException() {
            LoginRequest request = createLoginRequest("nonexistent", "anyPassword");
            when(userRepository.findByUsername("nonexistent")).thenReturn(Optional.empty());

            BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                    () -> authService.login(request));
            assertEquals("Invalid credentials", ex.getMessage());

            verify(userRepository, never()).save(any());
            verifyNoInteractions(auditService);
        }

        @Test
        @DisplayName("wrong password - throws BusinessRuleException and increments failure count")
        void login_wrongPassword_throwsAndIncrementsFailures() {
            User user = createEnabledUser();
            user.setFailedLoginAttempts(0);
            LoginRequest request = createLoginRequest("testuser", "wrongPass");

            when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(user));
            when(passwordEncoder.matches("wrongPass", "hashedPassword")).thenReturn(false);

            BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                    () -> authService.login(request));
            assertEquals("Invalid credentials", ex.getMessage());

            assertEquals(1, user.getFailedLoginAttempts());
            verify(userRepository).save(user);

            // Verify audit log for failed attempt
            verify(auditService).log(eq(1L), eq("testuser"), eq("AUTH"), eq("LOGIN_FAILED"),
                    eq("User"), eq(1L), eq("Failed login attempt 1"), isNull());
        }

        @Test
        @DisplayName("disabled account - throws BusinessRuleException")
        void login_disabledAccount_throwsException() {
            User user = createEnabledUser();
            user.setEnabled(false);
            LoginRequest request = createLoginRequest("testuser", "anyPass");

            when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(user));

            BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                    () -> authService.login(request));
            assertEquals("Account is disabled", ex.getMessage());

            verify(userRepository, never()).save(any());
            verify(passwordEncoder, never()).matches(anyString(), anyString());
        }

        @Test
        @DisplayName("locked account (not expired) - throws BusinessRuleException")
        void login_lockedAccountNotExpired_throwsException() {
            User user = createEnabledUser();
            user.setLocked(true);
            user.setLockExpiresAt(LocalDateTime.now().plusMinutes(10));
            LoginRequest request = createLoginRequest("testuser", "anyPass");

            when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(user));

            BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                    () -> authService.login(request));
            assertEquals("Account is locked. Try again later", ex.getMessage());

            assertTrue(user.isLocked());
            verify(passwordEncoder, never()).matches(anyString(), anyString());
        }

        @Test
        @DisplayName("locked account (expired lock) - auto-unlocks and allows login")
        void login_lockedAccountExpiredLock_autoUnlocksAndAllowsLogin() {
            User user = createEnabledUser();
            user.setLocked(true);
            user.setFailedLoginAttempts(5);
            user.setLockExpiresAt(LocalDateTime.now().minusMinutes(1));
            LoginRequest request = createLoginRequest("testuser", "correctPass");

            when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(user));
            when(passwordEncoder.matches("correctPass", "hashedPassword")).thenReturn(true);
            when(jwtProvider.generateAccessToken(1L, "testuser", "REVIEWER")).thenReturn("access-token");
            when(jwtProvider.generateRefreshToken(1L)).thenReturn("refresh-token");
            when(jwtProvider.getRefreshTokenExpirySeconds()).thenReturn(86400L);

            LoginResponse response = authService.login(request);

            assertNotNull(response);
            assertEquals("access-token", response.getAccessToken());

            // Verify account was unlocked
            assertFalse(user.isLocked());
            assertEquals(0, user.getFailedLoginAttempts());
            assertNull(user.getLockExpiresAt());
        }

        @Test
        @DisplayName("5 failed attempts - locks account")
        void login_fiveFailedAttempts_locksAccount() {
            User user = createEnabledUser();
            user.setFailedLoginAttempts(4); // This will be the 5th attempt
            LoginRequest request = createLoginRequest("testuser", "wrongPass");

            when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(user));
            when(passwordEncoder.matches("wrongPass", "hashedPassword")).thenReturn(false);

            BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                    () -> authService.login(request));
            assertEquals("Account is locked due to too many failed attempts", ex.getMessage());

            assertTrue(user.isLocked());
            assertEquals(5, user.getFailedLoginAttempts());
            assertNotNull(user.getLockExpiresAt());
            assertTrue(user.getLockExpiresAt().isAfter(LocalDateTime.now()));
            verify(userRepository).save(user);

            // Verify audit log for account lock
            verify(auditService).log(eq(1L), eq("testuser"), eq("AUTH"), eq("LOGIN_FAILED"),
                    eq("User"), eq(1L), contains("Account locked after 5 failed attempts"), isNull());
        }

        @Test
        @DisplayName("successful login after previous failures - resets failure count to 0")
        void login_successAfterFailures_resetsFailureCount() {
            User user = createEnabledUser();
            user.setFailedLoginAttempts(3);
            LoginRequest request = createLoginRequest("testuser", "correctPass");

            when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(user));
            when(passwordEncoder.matches("correctPass", "hashedPassword")).thenReturn(true);
            when(jwtProvider.generateAccessToken(1L, "testuser", "REVIEWER")).thenReturn("at");
            when(jwtProvider.generateRefreshToken(1L)).thenReturn("rt");
            when(jwtProvider.getRefreshTokenExpirySeconds()).thenReturn(86400L);

            authService.login(request);

            assertEquals(0, user.getFailedLoginAttempts());
            verify(userRepository).save(user);
        }
    }

    // ===========================
    // refresh() tests
    // ===========================

    @Nested
    @DisplayName("refresh")
    class RefreshTests {

        @Test
        @DisplayName("success - returns new tokens and revokes old")
        void refresh_success_returnsNewTokens() {
            User user = createEnabledUser();

            RefreshToken storedToken = new RefreshToken();
            storedToken.setId(10L);
            storedToken.setToken("valid-refresh-token");
            storedToken.setUserId(1L);
            storedToken.setExpiresAt(LocalDateTime.now().plusHours(1));
            storedToken.setRevoked(false);

            when(refreshTokenRepository.findByTokenAndRevokedFalse("valid-refresh-token"))
                    .thenReturn(Optional.of(storedToken));
            when(userRepository.findById(1L)).thenReturn(Optional.of(user));
            when(jwtProvider.generateAccessToken(1L, "testuser", "REVIEWER")).thenReturn("new-access-token");
            when(jwtProvider.generateRefreshToken(1L)).thenReturn("new-refresh-token");
            when(jwtProvider.getRefreshTokenExpirySeconds()).thenReturn(86400L);

            LoginResponse response = authService.refresh("valid-refresh-token");

            assertNotNull(response);
            assertEquals("new-access-token", response.getAccessToken());
            assertEquals("new-refresh-token", response.getRefreshToken());
            assertEquals("REVIEWER", response.getRole());
            assertFalse(response.isForcePasswordReset());

            // Verify old token was revoked
            assertTrue(storedToken.isRevoked());
            verify(refreshTokenRepository, times(2)).save(any(RefreshToken.class));
        }

        @Test
        @DisplayName("null token - throws BusinessRuleException")
        void refresh_nullToken_throwsException() {
            BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                    () -> authService.refresh(null));
            assertEquals("No refresh token provided", ex.getMessage());
        }

        @Test
        @DisplayName("blank token - throws BusinessRuleException")
        void refresh_blankToken_throwsException() {
            BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                    () -> authService.refresh("   "));
            assertEquals("No refresh token provided", ex.getMessage());
        }

        @Test
        @DisplayName("invalid/revoked token - throws BusinessRuleException")
        void refresh_invalidOrRevokedToken_throwsException() {
            when(refreshTokenRepository.findByTokenAndRevokedFalse("bad-token"))
                    .thenReturn(Optional.empty());

            BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                    () -> authService.refresh("bad-token"));
            assertEquals("Invalid or expired refresh token", ex.getMessage());
        }

        @Test
        @DisplayName("expired token - revokes token and throws BusinessRuleException")
        void refresh_expiredToken_revokesAndThrows() {
            RefreshToken storedToken = new RefreshToken();
            storedToken.setId(10L);
            storedToken.setToken("expired-token");
            storedToken.setUserId(1L);
            storedToken.setExpiresAt(LocalDateTime.now().minusHours(1));
            storedToken.setRevoked(false);

            when(refreshTokenRepository.findByTokenAndRevokedFalse("expired-token"))
                    .thenReturn(Optional.of(storedToken));

            BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                    () -> authService.refresh("expired-token"));
            assertEquals("Refresh token expired", ex.getMessage());

            assertTrue(storedToken.isRevoked());
            verify(refreshTokenRepository).save(storedToken);
        }

        @Test
        @DisplayName("disabled user - throws BusinessRuleException")
        void refresh_disabledUser_throwsException() {
            User user = createEnabledUser();
            user.setEnabled(false);

            RefreshToken storedToken = new RefreshToken();
            storedToken.setId(10L);
            storedToken.setToken("valid-token");
            storedToken.setUserId(1L);
            storedToken.setExpiresAt(LocalDateTime.now().plusHours(1));
            storedToken.setRevoked(false);

            when(refreshTokenRepository.findByTokenAndRevokedFalse("valid-token"))
                    .thenReturn(Optional.of(storedToken));
            when(userRepository.findById(1L)).thenReturn(Optional.of(user));

            BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                    () -> authService.refresh("valid-token"));
            assertEquals("Account is disabled", ex.getMessage());
        }

        @Test
        @DisplayName("locked user (lock not expired) - throws BusinessRuleException")
        void refresh_lockedUser_throwsException() {
            User user = createEnabledUser();
            user.setLocked(true);
            user.setLockExpiresAt(LocalDateTime.now().plusMinutes(10));

            RefreshToken storedToken = new RefreshToken();
            storedToken.setId(10L);
            storedToken.setToken("valid-token");
            storedToken.setUserId(1L);
            storedToken.setExpiresAt(LocalDateTime.now().plusHours(1));
            storedToken.setRevoked(false);

            when(refreshTokenRepository.findByTokenAndRevokedFalse("valid-token"))
                    .thenReturn(Optional.of(storedToken));
            when(userRepository.findById(1L)).thenReturn(Optional.of(user));

            BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                    () -> authService.refresh("valid-token"));
            assertEquals("Account is locked", ex.getMessage());
        }

        @Test
        @DisplayName("user not found for stored token - throws BusinessRuleException")
        void refresh_userNotFound_throwsException() {
            RefreshToken storedToken = new RefreshToken();
            storedToken.setId(10L);
            storedToken.setToken("valid-token");
            storedToken.setUserId(999L);
            storedToken.setExpiresAt(LocalDateTime.now().plusHours(1));
            storedToken.setRevoked(false);

            when(refreshTokenRepository.findByTokenAndRevokedFalse("valid-token"))
                    .thenReturn(Optional.of(storedToken));
            when(userRepository.findById(999L)).thenReturn(Optional.empty());

            BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                    () -> authService.refresh("valid-token"));
            assertEquals("User not found", ex.getMessage());
        }
    }

    // ===========================
    // logout() tests
    // ===========================

    @Nested
    @DisplayName("logout")
    class LogoutTests {

        @Test
        @DisplayName("revokes all refresh tokens for the user")
        void logout_revokesAllTokens() {
            authService.logout(1L);

            verify(refreshTokenRepository).revokeAllByUserId(1L);
        }
    }

    // ===========================
    // changePassword() tests
    // ===========================

    @Nested
    @DisplayName("changePassword")
    class ChangePasswordTests {

        @Test
        @DisplayName("success - updates hash and clears forcePasswordReset")
        void changePassword_success() {
            User user = createEnabledUser();
            user.setForcePasswordReset(true);
            ChangePasswordRequest request = createChangePasswordRequest("oldPass", "NewPass123");

            when(userRepository.findById(1L)).thenReturn(Optional.of(user));
            when(passwordEncoder.matches("oldPass", "hashedPassword")).thenReturn(true);
            when(passwordEncoder.encode("NewPass123")).thenReturn("newHashedPassword");

            authService.changePassword(1L, request);

            assertEquals("newHashedPassword", user.getPasswordHash());
            assertFalse(user.isForcePasswordReset());
            verify(userRepository).save(user);
        }

        @Test
        @DisplayName("wrong old password - throws BusinessRuleException")
        void changePassword_wrongOldPassword_throwsException() {
            User user = createEnabledUser();
            ChangePasswordRequest request = createChangePasswordRequest("wrongOldPass", "NewPass123");

            when(userRepository.findById(1L)).thenReturn(Optional.of(user));
            when(passwordEncoder.matches("wrongOldPass", "hashedPassword")).thenReturn(false);

            BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                    () -> authService.changePassword(1L, request));
            assertEquals("Current password is incorrect", ex.getMessage());

            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("weak new password (too short) - throws BusinessRuleException")
        void changePassword_weakNewPassword_throwsException() {
            User user = createEnabledUser();
            ChangePasswordRequest request = createChangePasswordRequest("oldPass", "Sh1");

            when(userRepository.findById(1L)).thenReturn(Optional.of(user));
            when(passwordEncoder.matches("oldPass", "hashedPassword")).thenReturn(true);

            BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                    () -> authService.changePassword(1L, request));
            assertEquals("Password must be at least 8 characters", ex.getMessage());

            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("weak new password (no numbers) - throws BusinessRuleException")
        void changePassword_noNumbers_throwsException() {
            User user = createEnabledUser();
            ChangePasswordRequest request = createChangePasswordRequest("oldPass", "AllLettersNoNumbers");

            when(userRepository.findById(1L)).thenReturn(Optional.of(user));
            when(passwordEncoder.matches("oldPass", "hashedPassword")).thenReturn(true);

            BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                    () -> authService.changePassword(1L, request));
            assertEquals("Password must contain both letters and numbers", ex.getMessage());
        }

        @Test
        @DisplayName("user not found - throws BusinessRuleException")
        void changePassword_userNotFound_throwsException() {
            ChangePasswordRequest request = createChangePasswordRequest("old", "NewPass123");

            when(userRepository.findById(999L)).thenReturn(Optional.empty());

            BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                    () -> authService.changePassword(999L, request));
            assertEquals("User not found", ex.getMessage());
        }
    }

    // ===========================
    // validatePasswordStrength() tests
    // ===========================

    @Nested
    @DisplayName("validatePasswordStrength")
    class ValidatePasswordStrengthTests {

        @Test
        @DisplayName("valid password - no exception")
        void validatePasswordStrength_valid_noException() {
            assertDoesNotThrow(() -> authService.validatePasswordStrength("ValidPass1"));
        }

        @Test
        @DisplayName("too short - throws BusinessRuleException")
        void validatePasswordStrength_tooShort_throwsException() {
            BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                    () -> authService.validatePasswordStrength("Ab1"));
            assertEquals("Password must be at least 8 characters", ex.getMessage());
        }

        @Test
        @DisplayName("null password - throws BusinessRuleException")
        void validatePasswordStrength_null_throwsException() {
            BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                    () -> authService.validatePasswordStrength(null));
            assertEquals("Password must be at least 8 characters", ex.getMessage());
        }

        @Test
        @DisplayName("no letters (digits only) - throws BusinessRuleException")
        void validatePasswordStrength_noLetters_throwsException() {
            BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                    () -> authService.validatePasswordStrength("123456789"));
            assertEquals("Password must contain both letters and numbers", ex.getMessage());
        }

        @Test
        @DisplayName("no numbers (letters only) - throws BusinessRuleException")
        void validatePasswordStrength_noNumbers_throwsException() {
            BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                    () -> authService.validatePasswordStrength("abcdefghi"));
            assertEquals("Password must contain both letters and numbers", ex.getMessage());
        }

        @Test
        @DisplayName("exactly 8 chars with letters and digits - valid")
        void validatePasswordStrength_exactMinLength_valid() {
            assertDoesNotThrow(() -> authService.validatePasswordStrength("Abcdefg1"));
        }
    }

    // ===========================
    // run() (CommandLineRunner) tests
    // ===========================

    @Nested
    @DisplayName("run (CommandLineRunner)")
    class RunTests {

        @Test
        @DisplayName("creates admin when no users exist")
        void run_noUsers_createsAdmin() {
            when(userRepository.count()).thenReturn(0L);
            when(passwordEncoder.encode("Admin123")).thenReturn("encodedAdmin123");

            authService.run();

            ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(userCaptor.capture());

            User savedUser = userCaptor.getValue();
            assertEquals("admin", savedUser.getUsername());
            assertEquals("encodedAdmin123", savedUser.getPasswordHash());
            assertEquals("System Administrator", savedUser.getFullName());
            assertEquals(Role.ADMIN, savedUser.getRole());
            assertEquals("", savedUser.getPhone());
            assertEquals("", savedUser.getEmail());
            assertTrue(savedUser.isForcePasswordReset());
        }

        @Test
        @DisplayName("skips admin creation when users already exist")
        void run_usersExist_skipsCreation() {
            when(userRepository.count()).thenReturn(5L);

            authService.run();

            verify(userRepository, never()).save(any());
            verify(passwordEncoder, never()).encode(anyString());
        }
    }
}
