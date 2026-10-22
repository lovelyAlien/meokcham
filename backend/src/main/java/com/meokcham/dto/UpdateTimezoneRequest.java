package com.meokcham.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateTimezoneRequest(@NotBlank String timezone) {
}
