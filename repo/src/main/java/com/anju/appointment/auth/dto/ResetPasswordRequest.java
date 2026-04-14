package com.anju.appointment.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ResetPasswordRequest {

    @NotBlank(message = "New password must not be blank")
    private String newPassword;

    private String verificationPassword;
}
