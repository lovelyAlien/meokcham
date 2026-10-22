package com.meokcham;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Set;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import com.meokcham.infra.OidcIdTokenVerifier;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;

/**
 * Apple/Google의 실제 공개키 대신 로컬 RSA 키로 서명해서, 발급자·대상·만료 검증 규칙만 확인한다.
 */
class OidcIdTokenValidatorTest {

	static final String APPLE = "https://appleid.apple.com";

	static NimbusJwtEncoder encoder;
	static JwtDecoder decoder;

	@BeforeAll
	static void setUp() throws Exception {
		RSAKey key = new RSAKeyGenerator(2048).keyID("test").generate();
		encoder = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(key)));
		NimbusJwtDecoder nimbus = NimbusJwtDecoder.withPublicKey(key.toRSAPublicKey()).build();
		nimbus.setJwtValidator(OidcIdTokenVerifier.validator(Set.of(APPLE), Set.of("com.meokcham.app")));
		decoder = nimbus;
	}

	@Test
	void acceptsTokenWithExpectedIssuerAndAudience() {
		assertThat(decoder.decode(token(APPLE, "com.meokcham.app", Instant.now().plusSeconds(600))).getSubject())
				.isEqualTo("sub-1");
	}

	@Test
	void rejectsWrongIssuer() {
		assertRejected(token("https://evil.example.com", "com.meokcham.app", Instant.now().plusSeconds(600)));
	}

	@Test
	void rejectsWrongAudience() {
		assertRejected(token(APPLE, "com.other.app", Instant.now().plusSeconds(600)));
	}

	@Test
	void rejectsExpiredToken() {
		assertRejected(token(APPLE, "com.meokcham.app", Instant.now().minusSeconds(600)));
	}

	@Test
	void rejectsEverythingWhenNoAudienceIsConfigured() {
		var validator = OidcIdTokenVerifier.validator(Set.of(APPLE), Set.of());
		var jwt = decoder.decode(token(APPLE, "com.meokcham.app", Instant.now().plusSeconds(600)));
		assertThat(validator.validate(jwt).hasErrors()).isTrue();
	}

	private static void assertRejected(String token) {
		assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(BadJwtException.class);
	}

	private static String token(String issuer, String audience, Instant expiresAt) {
		JwtClaimsSet claims = JwtClaimsSet.builder()
				.issuer(issuer)
				.audience(java.util.List.of(audience))
				.subject("sub-1")
				.issuedAt(expiresAt.minusSeconds(3600))
				.expiresAt(expiresAt)
				.build();
		return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(SignatureAlgorithm.RS256).keyId("test").build(), claims))
				.getTokenValue();
	}
}
