package com.anju.appointment.appointment.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CancelRequest {

    @NotBlank(message = "Reason must not be blank")
    private String reason;
}
