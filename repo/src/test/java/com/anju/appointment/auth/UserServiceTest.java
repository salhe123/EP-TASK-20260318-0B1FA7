package com.anju.appointment.auth;

import com.anju.appointment.audit.service.AuditService;
import com.anju.appointment.auth.dto.CreateUserRequest;
import com.anju.appointment.auth.dto.UpdateUserRequest;
import com.anju.appointment.auth.dto.UserResponse;
import com.anju.appointment.auth.entity.Role;
import com.anju.appointment.auth.entity.User;
import com.anju.appointment.auth.repository.UserRepository;
import com.anju.appointment.auth.service.AuthService;
import com.anju.appointment.auth.service.UserService;
import com.anju.appointment.common.BusinessRuleException;
import com.anju.appointment.common.ResourceNotFoundException;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AuthService authService;

    @Mock
    private AuditService auditService;

    @InjectMocks
    private UserService userService;

    @Captor
    private ArgumentCaptor<User> userCaptor;

    private static final Long ADMIN_USER_ID = 1L;
    private static final Long TARGET_USER_ID = 10L;

    // ---------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------

    private CreateUserRequest buildCreateUserRequest(String username, String role) {
        CreateUserRequest req = new CreateUserRequest();
        req.setUsername(username);
        req.setPassword("SecurePass123");
        req.setFullName("Test User");
        req.setRole(role);
        req.setPhone("13800138000");
        req.setEmail("test@example.com");
        return req;
    }

    private User buildUser(Long id, String username, Role role) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setPasswordHash("encoded-hash");
        user.setFullName("Test User");
        user.setRole(role);
        user.setPhone("13800138000");
        user.setEmail("test@example.com");
        user.setEnabled(true);
        user.setLocked(false);
        user.setFailedLoginAttempts(0);
        user.setForcePasswordReset(false);
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        return user;
    }

    // ---------------------------------------------------------------------------
    // createUser
    // ---------------------------------------------------------------------------

    @Nested
    class CreateUser {

        @Test
        void success() {
            CreateUserRequest request = buildCreateUserRequest("newuser", "ADMIN");
            User savedUser = buildUser(TARGET_USER_ID, "newuser", Role.ADMIN);
            savedUser.setForcePasswordReset(true);

            when(userRepository.existsByUsername("newuser")).thenReturn(false);
            doNothing().when(authService).validatePasswordStrength("SecurePass123");
            when(passwordEncoder.encode("SecurePass123")).thenReturn("encoded-hash");
            when(userRepository.save(any(User.class))).thenReturn(savedUser);

            UserResponse response = userService.createUser(request, ADMIN_USER_ID);

            assertThat(response.getId()).isEqualTo(TARGET_USER_ID);
            assertThat(response.getUsername()).isEqualTo("newuser");
            assertThat(response.getRole()).isEqualTo("ADMIN");
            assertThat(response.isForcePasswordReset()).isTrue();

            verify(userRepository).save(userCaptor.capture());
            User captured = userCaptor.getValue();
            assertThat(captured.getUsername()).isEqualTo("newuser");
            assertThat(captured.getPasswordHash()).isEqualTo("encoded-hash");
            assertThat(captured.getRole()).isEqualTo(Role.ADMIN);
            assertThat(captured.isForcePasswordReset()).isTrue();
        }

        @Test
        void usernameExistsThrows() {
            CreateUserRequest request = buildCreateUserRequest("existing", "ADMIN");
            when(userRepository.existsByUsername("existing")).thenReturn(true);

            assertThatThrownBy(() -> userService.createUser(request, ADMIN_USER_ID))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("Username already exists");

            verify(userRepository, never()).save(any());
        }

        @Test
        void invalidRoleThrows() {
            CreateUserRequest request = buildCreateUserRequest("newuser2", "SUPER_ADMIN");

            when(userRepository.existsByUsername("newuser2")).thenReturn(false);
            doNothing().when(authService).validatePasswordStrength("SecurePass123");

            assertThatThrownBy(() -> userService.createUser(request, ADMIN_USER_ID))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("Invalid role");

            verify(userRepository, never()).save(any());
        }

        @Test
        void auditLogged() {
            CreateUserRequest request = buildCreateUserRequest("audituser", "REVIEWER");
            User savedUser = buildUser(TARGET_USER_ID, "audituser", Role.REVIEWER);
            savedUser.setForcePasswordReset(true);

            when(userRepository.existsByUsername("audituser")).thenReturn(false);
            doNothing().when(authService).validatePasswordStrength("SecurePass123");
            when(passwordEncoder.encode("SecurePass123")).thenReturn("encoded-hash");
            when(userRepository.save(any(User.class))).thenReturn(savedUser);

            userService.createUser(request, ADMIN_USER_ID);

            verify(auditService).log(
                    eq(ADMIN_USER_ID), isNull(), eq("AUTH"), eq("CREATE"),
                    eq("User"), eq(TARGET_USER_ID),
                    anyString(), isNull());
        }
    }

    // ---------------------------------------------------------------------------
    // getUser
    // ---------------------------------------------------------------------------

    @Nested
    class GetUser {

        @Test
        void found() {
            User user = buildUser(TARGET_USER_ID, "testuser", Role.FINANCE);
            when(userRepository.findById(TARGET_USER_ID)).thenReturn(Optional.of(user));

            UserResponse response = userService.getUser(TARGET_USER_ID);

            assertThat(response.getId()).isEqualTo(TARGET_USER_ID);
            assertThat(response.getUsername()).isEqualTo("testuser");
            assertThat(response.getRole()).isEqualTo("FINANCE");
        }

        @Test
        void notFound() {
            when(userRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> userService.getUser(999L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("User not found");
        }
    }

    // ---------------------------------------------------------------------------
    // updateUser
    // ---------------------------------------------------------------------------

    @Nested
    class UpdateUser {

        @Test
        void partialFields() {
            User existingUser = buildUser(TARGET_USER_ID, "testuser", Role.REVIEWER);
            existingUser.setFullName("Old Name");
            existingUser.setPhone("11111111111");
            existingUser.setEmail("old@example.com");

            when(userRepository.findById(TARGET_USER_ID)).thenReturn(Optional.of(existingUser));

            User savedUser = buildUser(TARGET_USER_ID, "testuser", Role.REVIEWER);
            savedUser.setFullName("New Name");
            savedUser.setPhone("11111111111");
            savedUser.setEmail("new@example.com");

            when(userRepository.save(any(User.class))).thenReturn(savedUser);

            UpdateUserRequest request = new UpdateUserRequest();
            request.setFullName("New Name");
            request.setEmail("new@example.com");
            // role and phone are null -> should not be updated

            UserResponse response = userService.updateUser(TARGET_USER_ID, request, ADMIN_USER_ID);

            assertThat(response.getFullName()).isEqualTo("New Name");

            verify(userRepository).save(userCaptor.capture());
            User captured = userCaptor.getValue();
            assertThat(captured.getFullName()).isEqualTo("New Name");
            assertThat(captured.getEmail()).isEqualTo("new@example.com");
            // Phone should remain unchanged since request.phone was null
            assertThat(captured.getPhone()).isEqualTo("11111111111");
            // Role should remain unchanged since request.role was null
            assertThat(captured.getRole()).isEqualTo(Role.REVIEWER);
        }

        @Test
        void invalidRoleThrows() {
            User existingUser = buildUser(TARGET_USER_ID, "testuser", Role.REVIEWER);
            when(userRepository.findById(TARGET_USER_ID)).thenReturn(Optional.of(existingUser));

            UpdateUserRequest request = new UpdateUserRequest();
            request.setRole("NONEXISTENT_ROLE");

            assertThatThrownBy(() -> userService.updateUser(TARGET_USER_ID, request, ADMIN_USER_ID))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("Invalid role");

            verify(userRepository, never()).save(any());
        }

        @Test
        void auditLogged() {
            User existingUser = buildUser(TARGET_USER_ID, "testuser", Role.REVIEWER);
            when(userRepository.findById(TARGET_USER_ID)).thenReturn(Optional.of(existingUser));

            User savedUser = buildUser(TARGET_USER_ID, "testuser", Role.DISPATCHER);
            when(userRepository.save(any(User.class))).thenReturn(savedUser);

            UpdateUserRequest request = new UpdateUserRequest();
            request.setRole("DISPATCHER");

            userService.updateUser(TARGET_USER_ID, request, ADMIN_USER_ID);

            verify(auditService).log(
                    eq(ADMIN_USER_ID), isNull(), eq("AUTH"), eq("UPDATE"),
                    eq("User"), eq(TARGET_USER_ID),
                    anyString(), isNull());
        }
    }

    // ---------------------------------------------------------------------------
    // disableUser
    // ---------------------------------------------------------------------------

    @Nested
    class DisableUser {

        @Test
        void success() {
            User user = buildUser(TARGET_USER_ID, "testuser", Role.REVIEWER);
            user.setEnabled(true);
            when(userRepository.findById(TARGET_USER_ID)).thenReturn(Optional.of(user));

            User savedUser = buildUser(TARGET_USER_ID, "testuser", Role.REVIEWER);
            savedUser.setEnabled(false);
            when(userRepository.save(any(User.class))).thenReturn(savedUser);

            UserResponse response = userService.disableUser(TARGET_USER_ID, ADMIN_USER_ID);

            assertThat(response.isEnabled()).isFalse();

            verify(userRepository).save(userCaptor.capture());
            assertThat(userCaptor.getValue().isEnabled()).isFalse();
        }

        @Test
        void auditLogged() {
            User user = buildUser(TARGET_USER_ID, "testuser", Role.REVIEWER);
            when(userRepository.findById(TARGET_USER_ID)).thenReturn(Optional.of(user));

            User savedUser = buildUser(TARGET_USER_ID, "testuser", Role.REVIEWER);
            savedUser.setEnabled(false);
            when(userRepository.save(any(User.class))).thenReturn(savedUser);

            userService.disableUser(TARGET_USER_ID, ADMIN_USER_ID);

            verify(auditService).log(
                    eq(ADMIN_USER_ID), isNull(), eq("AUTH"), eq("UPDATE"),
                    eq("User"), eq(TARGET_USER_ID),
                    anyString(), isNull());
        }
    }

    // ---------------------------------------------------------------------------
    // enableUser
    // ---------------------------------------------------------------------------

    @Nested
    class EnableUser {

        @Test
        void success() {
            User user = buildUser(TARGET_USER_ID, "testuser", Role.REVIEWER);
            user.setEnabled(false);
            when(userRepository.findById(TARGET_USER_ID)).thenReturn(Optional.of(user));

            User savedUser = buildUser(TARGET_USER_ID, "testuser", Role.REVIEWER);
            savedUser.setEnabled(true);
            when(userRepository.save(any(User.class))).thenReturn(savedUser);

            UserResponse response = userService.enableUser(TARGET_USER_ID, ADMIN_USER_ID);

            assertThat(response.isEnabled()).isTrue();

            verify(userRepository).save(userCaptor.capture());
            assertThat(userCaptor.getValue().isEnabled()).isTrue();
        }

        @Test
        void auditLogged() {
            User user = buildUser(TARGET_USER_ID, "testuser", Role.REVIEWER);
            user.setEnabled(false);
            when(userRepository.findById(TARGET_USER_ID)).thenReturn(Optional.of(user));

            User savedUser = buildUser(TARGET_USER_ID, "testuser", Role.REVIEWER);
            savedUser.setEnabled(true);
            when(userRepository.save(any(User.class))).thenReturn(savedUser);

            userService.enableUser(TARGET_USER_ID, ADMIN_USER_ID);

            verify(auditService).log(
                    eq(ADMIN_USER_ID), isNull(), eq("AUTH"), eq("UPDATE"),
                    eq("User"), eq(TARGET_USER_ID),
                    anyString(), isNull());
        }
    }

    // ---------------------------------------------------------------------------
    // unlockUser
    // ---------------------------------------------------------------------------

    @Nested
    class UnlockUser {

        @Test
        void resetsLockFields() {
            User user = buildUser(TARGET_USER_ID, "testuser", Role.REVIEWER);
            user.setLocked(true);
            user.setFailedLoginAttempts(5);
            user.setLockExpiresAt(LocalDateTime.now().plusMinutes(10));
            when(userRepository.findById(TARGET_USER_ID)).thenReturn(Optional.of(user));

            User savedUser = buildUser(TARGET_USER_ID, "testuser", Role.REVIEWER);
            savedUser.setLocked(false);
            savedUser.setFailedLoginAttempts(0);
            savedUser.setLockExpiresAt(null);
            when(userRepository.save(any(User.class))).thenReturn(savedUser);

            UserResponse response = userService.unlockUser(TARGET_USER_ID, ADMIN_USER_ID);

            assertThat(response.isLocked()).isFalse();

            verify(userRepository).save(userCaptor.capture());
            User captured = userCaptor.getValue();
            assertThat(captured.isLocked()).isFalse();
            assertThat(captured.getFailedLoginAttempts()).isZero();
            assertThat(captured.getLockExpiresAt()).isNull();
        }

        @Test
        void auditLogged() {
            User user = buildUser(TARGET_USER_ID, "testuser", Role.REVIEWER);
            user.setLocked(true);
            user.setFailedLoginAttempts(5);
            when(userRepository.findById(TARGET_USER_ID)).thenReturn(Optional.of(user));

            User savedUser = buildUser(TARGET_USER_ID, "testuser", Role.REVIEWER);
            savedUser.setLocked(false);
            when(userRepository.save(any(User.class))).thenReturn(savedUser);

            userService.unlockUser(TARGET_USER_ID, ADMIN_USER_ID);

            verify(auditService).log(
                    eq(ADMIN_USER_ID), isNull(), eq("AUTH"), eq("UPDATE"),
                    eq("User"), eq(TARGET_USER_ID),
                    anyString(), isNull());
        }
    }

    // ---------------------------------------------------------------------------
    // resetPassword
    // ---------------------------------------------------------------------------

    @Nested
    class ResetPassword {

        @Test
        void successWithForcePasswordResetTrue() {
            User user = buildUser(TARGET_USER_ID, "testuser", Role.REVIEWER);
            user.setForcePasswordReset(false);
            when(userRepository.findById(TARGET_USER_ID)).thenReturn(Optional.of(user));
            doNothing().when(authService).validatePasswordStrength("NewPass456");
            when(passwordEncoder.encode("NewPass456")).thenReturn("new-encoded-hash");

            userService.resetPassword(TARGET_USER_ID, "NewPass456", ADMIN_USER_ID);

            verify(userRepository).save(userCaptor.capture());
            User captured = userCaptor.getValue();
            assertThat(captured.getPasswordHash()).isEqualTo("new-encoded-hash");
            assertThat(captured.isForcePasswordReset()).isTrue();
        }

        @Test
        void auditLogged() {
            User user = buildUser(TARGET_USER_ID, "testuser", Role.REVIEWER);
            when(userRepository.findById(TARGET_USER_ID)).thenReturn(Optional.of(user));
            doNothing().when(authService).validatePasswordStrength("NewPass789");
            when(passwordEncoder.encode("NewPass789")).thenReturn("new-encoded-hash");

            userService.resetPassword(TARGET_USER_ID, "NewPass789", ADMIN_USER_ID);

            verify(auditService).log(
                    eq(ADMIN_USER_ID), isNull(), eq("AUTH"), eq("UPDATE"),
                    eq("User"), eq(TARGET_USER_ID),
                    anyString(), isNull());
        }
    }
}
