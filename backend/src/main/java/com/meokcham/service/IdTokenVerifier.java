package com.meokcham.service;

import com.meokcham.domain.SocialProvider;

public interface IdTokenVerifier {

	/** 서명·발급자·대상(aud)·만료를 검증한다. 실패하면 INVALID_ID_TOKEN BusinessException. */
	SocialIdentity verify(SocialProvider provider, String idToken);
}
