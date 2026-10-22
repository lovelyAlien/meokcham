package com.meokcham.dto;

import jakarta.validation.constraints.NotNull;

// @NotNull은 검증이 아니라 OpenAPI 스펙에서 필수 필드로 표시하기 위한 것이다(앱 타입이 optional이 되지 않게).
public record TokenResponse(@NotNull String accessToken, @NotNull String refreshToken) {
}
