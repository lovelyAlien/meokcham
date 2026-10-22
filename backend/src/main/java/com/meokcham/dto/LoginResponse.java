package com.meokcham.dto;

import jakarta.validation.constraints.NotNull;

public record LoginResponse(@NotNull String accessToken, @NotNull String refreshToken, @NotNull boolean isNewUser,
		@NotNull UserSummary user) {

	public record UserSummary(@NotNull long id, @NotNull String timezone) {
	}
}
