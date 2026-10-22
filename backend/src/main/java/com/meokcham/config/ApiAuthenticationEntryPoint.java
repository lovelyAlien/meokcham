package com.meokcham.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import com.meokcham.exception.ErrorCode;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 인증 실패(401)를 다른 에러와 같은 ProblemDetail + code 형식으로 내려준다.
 * 만료된 Access 토큰은 앱이 재발급 후 재시도할 수 있도록 별도 코드를 쓴다.
 */
@Component
class ApiAuthenticationEntryPoint implements AuthenticationEntryPoint {

	@Override
	public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException e)
			throws IOException {
		ErrorCode code = isExpired(e) ? ErrorCode.ACCESS_TOKEN_EXPIRED : ErrorCode.UNAUTHORIZED;
		response.setStatus(HttpStatus.UNAUTHORIZED.value());
		response.setContentType("application/problem+json");
		response.setCharacterEncoding(StandardCharsets.UTF_8.name());
		response.getWriter().write("""
				{"type":"about:blank","title":"Unauthorized","status":401,"detail":"%s","code":"%s"}"""
				.formatted(code.defaultDetail(), code.name()));
	}

	// Spring Security가 만료를 별도 예외 타입으로 구분하지 않아 메시지로 판별한다.
	private static boolean isExpired(Throwable e) {
		for (Throwable t = e; t != null; t = t.getCause()) {
			if (t.getMessage() != null && t.getMessage().contains("expired")) {
				return true;
			}
		}
		return false;
	}
}
