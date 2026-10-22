package com.meokcham.dto;

import java.time.Instant;

import com.meokcham.domain.User;

import jakarta.validation.constraints.NotNull;

public record UserResponse(@NotNull long id, @NotNull String timezone, @NotNull Instant createdAt) {

	public static UserResponse from(User user) {
		return new UserResponse(user.getId(), user.getTimezone(), user.getCreatedAt());
	}
}
