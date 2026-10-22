package com.meokcham.infra;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;

import com.meokcham.config.AuthProperties;
import com.meokcham.domain.SocialProvider;
import com.meokcham.exception.BusinessException;
import com.meokcham.exception.ErrorCode;
import com.meokcham.service.IdTokenVerifier;
import com.meokcham.service.SocialIdentity;

/**
 * Apple/Google의 공개키(JWKS)로 ID 토큰을 검증한다.
 * shortcut: nonce를 검증하지 않아 탈취된 ID 토큰을 만료 전에 재사용할 수 있다, 공개 서비스로 전환할 때 nonce를 도입한다.
 */
@Component
public class OidcIdTokenVerifier implements IdTokenVerifier {

	private final Map<SocialProvider, JwtDecoder> decoders;

	OidcIdTokenVerifier(AuthProperties props) {
		this.decoders = Map.of(
				SocialProvider.APPLE, decoder("https://appleid.apple.com/auth/keys",
						Set.of("https://appleid.apple.com"), nonBlank(List.of(props.appleClientId()))),
				SocialProvider.GOOGLE, decoder("https://www.googleapis.com/oauth2/v3/certs",
						Set.of("https://accounts.google.com", "accounts.google.com"), nonBlank(props.googleClientIds())));
	}

	@Override
	public SocialIdentity verify(SocialProvider provider, String idToken) {
		try {
			Jwt jwt = decoders.get(provider).decode(idToken);
			return new SocialIdentity(jwt.getSubject(), jwt.getClaimAsString("email"));
		}
		catch (BadJwtException e) {
			throw new BusinessException(ErrorCode.INVALID_ID_TOKEN);
		}
	}

	// 공개키를 가져오지 못한 것 같은 서버 쪽 문제는 사용자 잘못이 아니므로 그대로 던져 500으로 처리한다.
	private static JwtDecoder decoder(String jwkSetUri, Set<String> issuers, Set<String> audiences) {
		NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri).jwsAlgorithm(SignatureAlgorithm.RS256).build();
		decoder.setJwtValidator(validator(issuers, audiences));
		return decoder;
	}

	/** 발급자·대상 검증. 설정된 aud가 하나도 없으면 모든 토큰을 거부한다. */
	public static OAuth2TokenValidator<Jwt> validator(Set<String> issuers, Set<String> audiences) {
		OAuth2TokenValidator<Jwt> issuer = jwt -> issuers.contains(jwt.getClaimAsString("iss"))
				? OAuth2TokenValidatorResult.success()
				: OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "발급자가 올바르지 않습니다.", null));
		OAuth2TokenValidator<Jwt> audience = jwt -> jwt.getAudience() != null
				&& jwt.getAudience().stream().anyMatch(audiences::contains)
						? OAuth2TokenValidatorResult.success()
						: OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "대상이 올바르지 않습니다.", null));
		return new DelegatingOAuth2TokenValidator<>(new JwtTimestampValidator(), issuer, audience);
	}

	private static Set<String> nonBlank(List<String> values) {
		return values.stream().filter(v -> !v.isBlank()).collect(Collectors.toSet());
	}
}
