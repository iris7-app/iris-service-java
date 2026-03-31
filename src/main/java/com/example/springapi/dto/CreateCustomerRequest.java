package com.example.springapi.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

// We can use records now !
public record CreateCustomerRequest(
        //with validation
        @NotBlank String name,
        @NotBlank @Email String email
) {
}
