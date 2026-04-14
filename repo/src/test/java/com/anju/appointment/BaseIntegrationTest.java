package com.anju.appointment;

import com.anju.appointment.auth.entity.Role;
import com.anju.appointment.auth.entity.User;
import com.anju.appointment.auth.repository.RefreshTokenRepository;
import com.anju.appointment.auth.repository.UserRepository;
import com.anju.appointment.auth.security.JwtProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class BaseIntegrationTest {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected UserRepository userRepository;

    @Autowired
    protected RefreshTokenRepository refreshTokenRepository;

    @Autowired
    protected JwtProvider jwtProvider;

    @Autowired
    protected PasswordEncoder passwordEncoder;

    protected User adminUser;
    protected User dispatcherUser;
    protected User reviewerUser;
    protected User financeUser;

    protected String adminToken;
    protected String dispatcherToken;
    protected String reviewerToken;
    protected String financeToken;

    @BeforeEach
    void setUpBase() {
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();

        adminUser = createUser("admin", "Admin123", "System Admin", Role.ADMIN);
        dispatcherUser = createUser("dispatcher", "Dispatch1", "Dispatcher User", Role.DISPATCHER);
        reviewerUser = createUser("reviewer", "Reviewer1", "Reviewer User", Role.REVIEWER);
        financeUser = createUser("finance", "Finance1", "Finance User", Role.FINANCE);

        adminToken = generateToken(adminUser);
        dispatcherToken = generateToken(dispatcherUser);
        reviewerToken = generateToken(reviewerUser);
        financeToken = generateToken(financeUser);
    }

    protected User createUser(String username, String password, String fullName, Role role) {
        User user = new User();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setFullName(fullName);
        user.setRole(role);
        user.setPhone("13800138000");
        user.setEmail(username + "@test.com");
        user.setEnabled(true);
        user.setForcePasswordReset(false);
        return userRepository.save(user);
    }

    protected String generateToken(User user) {
        return jwtProvider.generateAccessToken(user.getId(), user.getUsername(), user.getRole().name());
    }
}
