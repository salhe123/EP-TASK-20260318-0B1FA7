package com.anju.appointment.auth.security;

import com.anju.appointment.auth.entity.User;
import com.anju.appointment.auth.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class ForcePasswordResetFilter extends OncePerRequestFilter {

    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    public ForcePasswordResetFilter(UserRepository userRepository, ObjectMapper objectMapper) {
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedUser principal) {
            String path = request.getRequestURI();
            boolean isAllowed = path.equals("/api/auth/change-password")
                    || path.equals("/api/auth/logout")
                    || path.equals("/api/auth/refresh");

            if (!isAllowed) {
                User user = userRepository.findById(principal.getUserId()).orElse(null);
                if (user != null && user.isForcePasswordReset()) {
                    response.setStatus(HttpStatus.CONFLICT.value());
                    response.setContentType(MediaType.APPLICATION_JSON_VALUE);

                    Map<String, Object> body = new LinkedHashMap<>();
                    body.put("timestamp", LocalDateTime.now().toString());
                    body.put("status", HttpStatus.CONFLICT.value());
                    body.put("error", HttpStatus.CONFLICT.getReasonPhrase());
                    body.put("message", "Password change required");
                    objectMapper.writeValue(response.getOutputStream(), body);
                    return;
                }
            }
        }

        filterChain.doFilter(request, response);
    }
}
