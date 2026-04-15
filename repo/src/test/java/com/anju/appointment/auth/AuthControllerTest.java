package com.anju.appointment.auth;

import com.anju.appointment.BaseIntegrationTest;
import com.anju.appointment.auth.entity.Role;
import com.anju.appointment.auth.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthControllerTest extends BaseIntegrationTest {

    @Test
    void login_success_returnsJwtAndCookies() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"Admin123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken", notNullValue()))
                .andExpect(jsonPath("$.role", is("ADMIN")))
                .andExpect(header().exists("Set-Cookie"));
    }

    @Test
    void login_wrongPassword_returns409() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"WrongPass1\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", is("Invalid credentials")));
    }

    @Test
    void login_fiveWrongAttempts_locksAccount() throws Exception {
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"username\":\"admin\",\"password\":\"WrongPass1\"}"));
        }

        // 6th attempt should see locked account
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"Admin123\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", is("Account is locked. Try again later")));
    }

    @Test
    void login_lockedAccount_returns409() throws Exception {
        // Lock the account
        User user = userRepository.findByUsername("dispatcher").orElseThrow();
        user.setLocked(true);
        user.setLockExpiresAt(java.time.LocalDateTime.now().plusMinutes(15));
        userRepository.save(user);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"dispatcher\",\"password\":\"Dispatch1\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", is("Account is locked. Try again later")));
    }

    @Test
    void login_afterLockExpires_succeeds() throws Exception {
        // Lock the account with expiry in the past
        User user = userRepository.findByUsername("dispatcher").orElseThrow();
        user.setLocked(true);
        user.setFailedLoginAttempts(5);
        user.setLockExpiresAt(java.time.LocalDateTime.now().minusMinutes(1));
        userRepository.save(user);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"dispatcher\",\"password\":\"Dispatch1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken", notNullValue()));
    }

    @Test
    void accessProtectedEndpoint_noToken_returns401() throws Exception {
        mockMvc.perform(get("/api/properties"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void accessProtectedEndpoint_invalidToken_returns401() throws Exception {
        mockMvc.perform(get("/api/properties")
                        .header("Authorization", "Bearer invalid.jwt.token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void accessAdminEndpoint_wrongRole_returns403() throws Exception {
        mockMvc.perform(get("/api/admin/users")
                        .header("Authorization", "Bearer " + financeToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void changePassword_wrongOldPassword_returns409() throws Exception {
        mockMvc.perform(post("/api/auth/change-password")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"oldPassword\":\"WrongOld1\",\"newPassword\":\"NewPass123!xx\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", is("Current password is incorrect")));
    }

    @Test
    void changePassword_weakPassword_returns409() throws Exception {
        mockMvc.perform(post("/api/auth/change-password")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"oldPassword\":\"Admin123\",\"newPassword\":\"short\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("at least")));
    }

    @Test
    void changePassword_noSpecialChar_returns409() throws Exception {
        mockMvc.perform(post("/api/auth/change-password")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"oldPassword\":\"Admin123\",\"newPassword\":\"Abcdefgh1234\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("special character")));
    }

    @Test
    void changePassword_valid_succeeds() throws Exception {
        mockMvc.perform(post("/api/auth/change-password")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"oldPassword\":\"Admin123\",\"newPassword\":\"NewSecure1!xx\"}"))
                .andExpect(status().isOk());

        // Verify new password works
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"NewSecure1!xx\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void forcePasswordReset_blocksOtherEndpoints() throws Exception {
        User user = createUser("newuser", "Initial1", "New User", Role.DISPATCHER);
        user.setForcePasswordReset(true);
        userRepository.save(user);
        String token = generateToken(user);

        // Should be blocked from accessing other endpoints
        mockMvc.perform(get("/api/properties")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", is("Password change required")));

        // But can still change password
        mockMvc.perform(post("/api/auth/change-password")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"oldPassword\":\"Initial1\",\"newPassword\":\"Changed1!xxxY\"}"))
                .andExpect(status().isOk());
    }
}
