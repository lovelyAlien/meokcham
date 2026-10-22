package com.meokcham.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.meokcham.config.AuthProperties;
import com.meokcham.domain.NotificationSetting;
import com.meokcham.domain.RefreshToken;
import com.meokcham.domain.SocialAccount;
import com.meokcham.domain.SocialProvider;
import com.meokcham.domain.User;
import com.meokcham.dto.LoginResponse;
import com.meokcham.dto.TokenResponse;
import com.meokcham.exception.BusinessException;
import com.meokcham.exception.ErrorCode;
import com.meokcham.repository.NotificationSettingRepository;
import com.meokcham.repository.RefreshTokenRepository;
import com.meokcham.repository.SocialAccountRepository;
import com.meokcham.repository.UserRepository;

@Service
public class AuthService {

	private static final SecureRandom RANDOM = new SecureRandom();

	private final IdTokenVerifier idTokenVerifier;
	private final UserRepository users;
	private final SocialAccountRepository socialAccounts;
	private final NotificationSettingRepository notificationSettings;
	private final RefreshTokenRepository refreshTokens;
	private final JwtEncoder jwtEncoder;
	private final AuthProperties props;
	private final Clock clock;

	public AuthService(IdTokenVerifier idTokenVerifier, UserRepository users, SocialAccountRepository socialAccounts,
			NotificationSettingRepository notificationSettings, RefreshTokenRepository refreshTokens,
			JwtEncoder jwtEncoder, AuthProperties props, Clock clock) {
		this.idTokenVerifier = idTokenVerifier;
		this.users = users;
		this.socialAccounts = socialAccounts;
		this.notificationSettings = notificationSettings;
		this.refreshTokens = refreshTokens;
		this.jwtEncoder = jwtEncoder;
		this.props = props;
		this.clock = clock;
	}

	/**
	 * 소셜 로그인. 처음이면 가입(사용자, 소셜 계정, 기본 알림 설정)까지 한 번에 처리한다.
	 * shortcut: 같은 계정이 동시에 처음 로그인하면 유니크 제약 위반으로 한쪽이 500이 된다, 앱이 재시도하므로 두고 문제가 되면 잡는다.
	 */
	@Transactional
	public LoginResponse login(SocialProvider provider, String idToken, String timezone) {
		TimezoneValidator.requireValid(timezone);
		SocialIdentity identity = idTokenVerifier.verify(provider, idToken);

		var existing = socialAccounts.findByProviderAndProviderSubject(provider, identity.subject());
		boolean isNewUser = existing.isEmpty();
		User user = existing.map(SocialAccount::getUser).orElseGet(() -> register(provider, identity, timezone));

		TokenResponse tokens = issueTokens(user, UUID.randomUUID());
		return new LoginResponse(tokens.accessToken(), tokens.refreshToken(), isNewUser,
				new LoginResponse.UserSummary(user.getId(), user.getTimezone()));
	}

	/**
	 * 재발급. 쓸 때마다 새 토큰으로 교체하고, 이미 폐기된 토큰이 오면 탈취로 보고 같은 family를 모두 폐기한다.
	 * 폐기를 롤백하지 않도록 BusinessException에도 커밋한다.
	 */
	@Transactional(noRollbackFor = BusinessException.class)
	public TokenResponse refresh(String rawRefreshToken) {
		Instant now = clock.instant();
		RefreshToken token = refreshTokens.findByTokenHashForUpdate(hash(rawRefreshToken))
				.orElseThrow(() -> new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN));
		if (token.isRevoked()) {
			refreshTokens.revokeFamily(token.getFamilyId(), now);
			throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN);
		}
		if (token.getExpiresAt().isBefore(now)) {
			throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN);
		}
		token.revoke();
		return issueTokens(token.getUser(), token.getFamilyId());
	}

	/** 이 기기(family)의 세션을 폐기한다. 없거나 남의 토큰이어도 성공으로 응답해 존재 여부를 알리지 않는다. */
	@Transactional
	public void logout(long userId, String rawRefreshToken) {
		refreshTokens.findByTokenHashForUpdate(hash(rawRefreshToken))
				.filter(token -> token.getUser().getId() == userId)
				.ifPresent(token -> refreshTokens.revokeFamily(token.getFamilyId(), clock.instant()));
	}

	private User register(SocialProvider provider, SocialIdentity identity, String timezone) {
		User user = users.save(new User(timezone));
		socialAccounts.save(new SocialAccount(user, provider, identity.subject(), identity.email()));
		notificationSettings.save(new NotificationSetting(user));
		return user;
	}

	private TokenResponse issueTokens(User user, UUID familyId) {
		Instant now = clock.instant();
		String rawRefresh = "r_" + Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes(32));
		refreshTokens.save(new RefreshToken(user, hash(rawRefresh), familyId, now.plus(props.refreshTtl())));

		JwtClaimsSet claims = JwtClaimsSet.builder()
				.subject(String.valueOf(user.getId()))
				.issuedAt(now)
				.expiresAt(now.plus(props.accessTtl()))
				.build();
		String access = jwtEncoder
				.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
				.getTokenValue();
		return new TokenResponse(access, rawRefresh);
	}

	private static byte[] randomBytes(int length) {
		byte[] bytes = new byte[length];
		RANDOM.nextBytes(bytes);
		return bytes;
	}

	private static String hash(String raw) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8)));
		}
		catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}
}
