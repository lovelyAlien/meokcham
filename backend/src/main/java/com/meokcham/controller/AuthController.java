package com.meokcham.controller;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.meokcham.dto.LoginRequest;
import com.meokcham.dto.LoginResponse;
import com.meokcham.dto.RefreshRequest;
import com.meokcham.dto.TokenResponse;
import com.meokcham.service.AuthService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

	private final AuthService authService;

	public AuthController(AuthService authService) {
		this.authService = authService;
	}

	@PostMapping("/login")
	LoginResponse login(@Valid @RequestBody LoginRequest request) {
		return authService.login(request.provider(), request.idToken(), request.timezone());
	}

	@PostMapping("/refresh")
	TokenResponse refresh(@Valid @RequestBody RefreshRequest request) {
		return authService.refresh(request.refreshToken());
	}

	@PostMapping("/logout")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void logout(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody RefreshRequest request) {
		authService.logout(Long.parseLong(jwt.getSubject()), request.refreshToken());
	}
}
