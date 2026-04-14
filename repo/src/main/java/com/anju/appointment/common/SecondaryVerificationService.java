package com.anju.appointment.common;

import com.anju.appointment.auth.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class SecondaryVerificationService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final boolean enabled;

    public SecondaryVerificationService(UserRepository userRepository,
                                         PasswordEncoder passwordEncoder,
                                         @Value("${app.security.secondary-verification:false}") boolean enabled) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void verify(Long userId, String password) {
        if (!enabled) {
            return;
        }
        if (password == null || password.isBlank()) {
            throw new BusinessRuleException("Secondary verification required: please provide your current password");
        }
        userRepository.findById(userId).ifPresentOrElse(
                user -> {
                    if (!passwordEncoder.matches(password, user.getPasswordHash())) {
                        throw new BusinessRuleException("Secondary verification failed: incorrect password");
                    }
                },
                () -> { throw new BusinessRuleException("User not found for verification"); }
        );
    }
}
