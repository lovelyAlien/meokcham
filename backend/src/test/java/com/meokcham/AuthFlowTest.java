package com.meokcham;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.web.servlet.MockMvc;

import com.jayway.jsonpath.JsonPath;
import com.meokcham.domain.SocialProvider;
import com.meokcham.exception.BusinessException;
import com.meokcham.exception.ErrorCode;
import com.meokcham.service.IdTokenVerifier;
import com.meokcham.service.SocialIdentity;

/**
 * 로그인 → 보호된 API → 재발급(rotation·재사용 탐지) → 로그아웃 → 계정 삭제 흐름을 실제 DB로 검증한다.
 * 소셜 ID 토큰 검증은 외부 의존이라 "<subject>"를 그대로 신원으로 받아들이는 대역으로 바꾼다.
 */
@Import({ TestcontainersConfiguration.class, AuthFlowTest.FakeVerifierConfig.class })
@SpringBootTest
@AutoConfigureMockMvc
class AuthFlowTest {

	@TestConfiguration
	static class FakeVerifierConfig {

		@Bean
		@Primary
		IdTokenVerifier fakeIdTokenVerifier() {
			return (SocialProvider provider, String idToken) -> {
				if (idToken.equals("invalid")) {
					throw new BusinessException(ErrorCode.INVALID_ID_TOKEN);
				}
				return new SocialIdentity(idToken, null);
			};
		}
	}

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcClient jdbc;

	@Autowired
	JwtEncoder jwtEncoder;

	@Test
	void firstLoginRegistersUserWithDefaultsAndSecondLoginIsNotNew() throws Exception {
		String subject = subject();

		String first = login(subject, "Asia/Seoul").andExpect(status().isOk())
				.andExpect(jsonPath("$.isNewUser").value(true))
				.andExpect(jsonPath("$.user.timezone").value("Asia/Seoul"))
				.andReturn().getResponse().getContentAsString();
		long userId = JsonPath.<Number>read(first, "$.user.id").longValue();

		var settings = jdbc.sql("SELECT enabled FROM notification_settings WHERE user_id = :id")
				.param("id", userId).query(Boolean.class).single();
		assertThat(settings).isTrue();

		login(subject, "America/Los_Angeles").andExpect(status().isOk())
				.andExpect(jsonPath("$.isNewUser").value(false))
				.andExpect(jsonPath("$.user.id").value(userId))
				// 기존 사용자의 타임존은 로그인으로 바뀌지 않는다.
				.andExpect(jsonPath("$.user.timezone").value("Asia/Seoul"));
	}

	@Test
	void invalidIdTokenIsUnauthorized() throws Exception {
		login("invalid", "Asia/Seoul").andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("INVALID_ID_TOKEN"));
	}

	@Test
	void unknownTimezoneIsRejected() throws Exception {
		login(subject(), "+09:00").andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
		login(subject(), "Mars/Base").andExpect(status().isBadRequest());
	}

	@Test
	void protectedApiRequiresValidAccessToken() throws Exception {
		mvc.perform(get("/api/v1/users/me")).andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
		mvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer garbage"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

		Tokens tokens = registerUser();
		mvc.perform(get("/api/v1/users/me").header("Authorization", bearer(tokens)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.timezone").value("Asia/Seoul"));
	}

	@Test
	void expiredAccessTokenHasItsOwnCode() throws Exception {
		Tokens tokens = registerUser();
		// 기본 시계 오차(60초)를 넘겨 확실히 만료된 토큰
		Instant past = Instant.now().minusSeconds(600);
		JwtClaimsSet claims = JwtClaimsSet.builder()
				.subject(String.valueOf(tokens.userId())).issuedAt(past.minusSeconds(900)).expiresAt(past).build();
		String expired = jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
				.getTokenValue();

		mvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + expired))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("ACCESS_TOKEN_EXPIRED"));
	}

	@Test
	void refreshRotatesAndReuseRevokesTheWholeFamily() throws Exception {
		Tokens first = registerUser();

		String body = refresh(first.refresh()).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		String second = JsonPath.read(body, "$.refreshToken");
		assertThat(second).isNotEqualTo(first.refresh());

		// 이미 쓴 토큰을 다시 내면 탈취로 보고 거부하고, 그 뒤에 발급된 토큰까지 폐기한다.
		refresh(first.refresh()).andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
		refresh(second).andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
	}

	@Test
	void unknownRefreshTokenIsRejected() throws Exception {
		refresh("r_does-not-exist").andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
	}

	@Test
	void logoutRevokesOnlyThatSession() throws Exception {
		String subject = subject();
		Tokens deviceA = loginTokens(subject);
		Tokens deviceB = loginTokens(subject);

		mvc.perform(post("/api/v1/auth/logout").header("Authorization", bearer(deviceA))
				.contentType(MediaType.APPLICATION_JSON).content(json("refreshToken", deviceA.refresh())))
				.andExpect(status().isNoContent());

		refresh(deviceA.refresh()).andExpect(status().isUnauthorized());
		refresh(deviceB.refresh()).andExpect(status().isOk());
	}

	@Test
	void changingTimezoneUpdatesUser() throws Exception {
		Tokens tokens = registerUser();

		mvc.perform(patch("/api/v1/users/me").header("Authorization", bearer(tokens))
				.contentType(MediaType.APPLICATION_JSON).content(json("timezone", "America/Los_Angeles")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.timezone").value("America/Los_Angeles"));
	}

	@Test
	void deletingAccountRemovesAllDataAndInvalidatesTheToken() throws Exception {
		Tokens tokens = registerUser();

		mvc.perform(delete("/api/v1/users/me").header("Authorization", bearer(tokens))).andExpect(status().isNoContent());

		long remaining = jdbc.sql("""
				SELECT (SELECT count(*) FROM users WHERE id = :id)
				     + (SELECT count(*) FROM social_accounts WHERE user_id = :id)
				     + (SELECT count(*) FROM refresh_tokens WHERE user_id = :id)
				     + (SELECT count(*) FROM notification_settings WHERE user_id = :id)
				""").param("id", tokens.userId()).query(Long.class).single();
		assertThat(remaining).isZero();

		// 만료 전 Access 토큰이어도 계정이 없으면 인증 실패다.
		mvc.perform(get("/api/v1/users/me").header("Authorization", bearer(tokens))).andExpect(status().isUnauthorized());
	}

	private Tokens registerUser() throws Exception {
		return loginTokens(subject());
	}

	private Tokens loginTokens(String subject) throws Exception {
		String body = login(subject, "Asia/Seoul").andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		return new Tokens(JsonPath.<Number>read(body, "$.user.id").longValue(), JsonPath.read(body, "$.accessToken"),
				JsonPath.read(body, "$.refreshToken"));
	}

	private org.springframework.test.web.servlet.ResultActions login(String idToken, String timezone) throws Exception {
		return mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"provider\":\"APPLE\",\"idToken\":\"%s\",\"timezone\":\"%s\"}".formatted(idToken, timezone)));
	}

	private org.springframework.test.web.servlet.ResultActions refresh(String refreshToken) throws Exception {
		return mvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
				.content(json("refreshToken", refreshToken)));
	}

	private static String json(String key, String value) {
		return "{\"%s\":\"%s\"}".formatted(key, value);
	}

	private static String bearer(Tokens tokens) {
		return "Bearer " + tokens.access();
	}

	private static String subject() {
		return "sub-" + UUID.randomUUID();
	}

	record Tokens(long userId, String access, String refresh) {
	}
}
