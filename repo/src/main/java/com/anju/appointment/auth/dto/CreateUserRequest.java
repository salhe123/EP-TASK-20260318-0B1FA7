package com.anju.appointment.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateUserRequest {

    @NotBlank(message = "Username must not be blank")
    private String username;

    @NotBlank(message = "Password must not be blank")
    private String password;

    @NotBlank(message = "Full name must not be blank")
    private String fullName;

    @NotNull(message = "Role must not be null")
    private String role;

    private String phone;

    @Email(message = "Email must be valid")
    private String email;
}
