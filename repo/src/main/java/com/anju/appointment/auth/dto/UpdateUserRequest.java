package com.anju.appointment.auth.dto;

import jakarta.validation.constraints.Email;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateUserRequest {

    private String fullName;
    private String role;
    private String phone;

    @Email(message = "Email must be valid")
    private String email;
}
