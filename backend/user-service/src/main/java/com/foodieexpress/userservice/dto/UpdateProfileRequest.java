package com.foodieexpress.userservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
        @NotBlank @Size(max = 100) String displayName,
        @Size(max = 30) @Pattern(regexp = "^$|^[+0-9() .-]{7,30}$") String phone) {
}
