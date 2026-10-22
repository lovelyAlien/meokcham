package com.meokcham.config;

import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 인증 설정. 시크릿과 클라이언트 ID는 환경 변수로 주입하며 저장소에 넣지 않는다.
 */
@Validated
@ConfigurationProperties("meokcham.auth")
public record AuthProperties(
		/* HS256 서명 키. 32바이트 이상이 아니면 기동하지 않는다. */
		@Size(min = 32) String jwtSecret,
		@NotNull Duration accessTtl,
		@NotNull Duration refreshTtl,
		/* Apple ID 토큰의 aud. iOS 번들 ID. */
		String appleClientId,
		/* Google ID 토큰의 aud 후보. iOS/Android/Web 클라이언트 ID. */
		List<String> googleClientIds) {

	public AuthProperties {
		appleClientId = appleClientId == null ? "" : appleClientId;
		googleClientIds = googleClientIds == null ? List.of() : googleClientIds;
	}
}
