package com.anju.appointment.auth.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class LoginResponse {

    private String accessToken;
    private String refreshToken;
    private boolean forcePasswordReset;
    private String role;
}
