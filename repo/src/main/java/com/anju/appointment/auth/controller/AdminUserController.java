package com.anju.appointment.auth.controller;

import com.anju.appointment.auth.dto.CreateUserRequest;
import com.anju.appointment.auth.dto.ResetPasswordRequest;
import com.anju.appointment.auth.dto.UpdateUserRequest;
import com.anju.appointment.auth.dto.UserResponse;
import com.anju.appointment.auth.security.AuthenticatedUser;
import com.anju.appointment.auth.service.UserService;
import com.anju.appointment.common.SecondaryVerificationService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/users")
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {

    private final UserService userService;
    private final SecondaryVerificationService secondaryVerification;

    public AdminUserController(UserService userService,
                                SecondaryVerificationService secondaryVerification) {
        this.userService = userService;
        this.secondaryVerification = secondaryVerification;
    }

    @PostMapping
    public ResponseEntity<UserResponse> createUser(@Valid @RequestBody CreateUserRequest request,
                                                    @AuthenticationPrincipal AuthenticatedUser principal) {
        return ResponseEntity.ok(userService.createUser(request, principal.getUserId()));
    }

    @GetMapping
    public ResponseEntity<Page<UserResponse>> listUsers(
            @RequestParam(required = false) String role,
            @RequestParam(required = false) Boolean enabled,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(userService.listUsers(role, enabled, pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<UserResponse> getUser(@PathVariable Long id) {
        return ResponseEntity.ok(userService.getUser(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<UserResponse> updateUser(@PathVariable Long id,
                                                    @Valid @RequestBody UpdateUserRequest request,
                                                    @AuthenticationPrincipal AuthenticatedUser principal) {
        return ResponseEntity.ok(userService.updateUser(id, request, principal.getUserId()));
    }

    @PutMapping("/{id}/disable")
    public ResponseEntity<UserResponse> disableUser(@PathVariable Long id,
                                                     @AuthenticationPrincipal AuthenticatedUser principal) {
        return ResponseEntity.ok(userService.disableUser(id, principal.getUserId()));
    }

    @PutMapping("/{id}/enable")
    public ResponseEntity<UserResponse> enableUser(@PathVariable Long id,
                                                    @AuthenticationPrincipal AuthenticatedUser principal) {
        return ResponseEntity.ok(userService.enableUser(id, principal.getUserId()));
    }

    @PutMapping("/{id}/unlock")
    public ResponseEntity<UserResponse> unlockUser(@PathVariable Long id,
                                                    @AuthenticationPrincipal AuthenticatedUser principal) {
        return ResponseEntity.ok(userService.unlockUser(id, principal.getUserId()));
    }

    @PutMapping("/{id}/reset-password")
    public ResponseEntity<Void> resetPassword(@PathVariable Long id,
                                               @Valid @RequestBody ResetPasswordRequest request,
                                               @AuthenticationPrincipal AuthenticatedUser principal) {
        secondaryVerification.verify(principal.getUserId(), request.getVerificationPassword());
        userService.resetPassword(id, request.getNewPassword(), principal.getUserId());
        return ResponseEntity.ok().build();
    }
}
