package com.anju.appointment.auth.dto;

import com.anju.appointment.auth.entity.User;
import com.anju.appointment.common.DataMaskingUtil;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class UserResponse {

    private Long id;
    private String username;
    private String fullName;
    private String role;
    private String phone;
    private String email;
    private boolean enabled;
    private boolean locked;
    private boolean forcePasswordReset;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static UserResponse fromEntity(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .fullName(user.getFullName())
                .role(user.getRole().name())
                .phone(DataMaskingUtil.maskPhone(user.getPhone()))
                .email(DataMaskingUtil.maskEmail(user.getEmail()))
                .enabled(user.isEnabled())
                .locked(user.isLocked())
                .forcePasswordReset(user.isForcePasswordReset())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }
}
